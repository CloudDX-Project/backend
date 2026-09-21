param(
    [string]$Region = "ap-northeast-2",
    [string]$Cluster = "ai-travel-eks-cluster",
    [string]$Namespace = "travel",
    [string]$Repository = "ai-travel-backend",
    [string]$Tag = ""
)

$ErrorActionPreference = "Stop"
$backendRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $backendRoot

function Run-Step([string]$Name, [scriptblock]$Action) {
    Write-Host "`n== $Name ==" -ForegroundColor Cyan
    & $Action
    if ($LASTEXITCODE -ne 0) { throw "$Name failed with exit code $LASTEXITCODE" }
}

$commit = (git rev-parse --short HEAD).Trim()
if ([string]::IsNullOrWhiteSpace($Tag)) {
    $Tag = "manual-$commit-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
}

$account = (aws sts get-caller-identity --query Account --output text).Trim()
$registry = "$account.dkr.ecr.$Region.amazonaws.com"
$image = "$registry/$Repository`:$Tag"

$previousApi = kubectl get deployment travel-api -n $Namespace -o jsonpath="{.spec.template.spec.containers[0].image}" 2>$null
$previousWorker = kubectl get deployment travel-worker -n $Namespace -o jsonpath="{.spec.template.spec.containers[0].image}" 2>$null

Run-Step "백엔드 테스트와 JAR 생성" { .\gradlew.bat clean test bootJar --console=plain }
Run-Step "Docker 이미지 생성" { docker build --label "org.opencontainers.image.revision=$commit" -t $image . }

Write-Host "`n== ECR 로그인 ==" -ForegroundColor Cyan
$password = aws ecr get-login-password --region $Region
$password | docker login --username AWS --password-stdin $registry
if ($LASTEXITCODE -ne 0) { throw "ECR login failed" }

Run-Step "ECR 이미지 업로드" { docker push $image }
Run-Step "EKS 연결" { aws eks update-kubeconfig --name $Cluster --region $Region }

try {
    Run-Step "API 이미지 교체" { kubectl set image deployment/travel-api travel-api=$image -n $Namespace }
    Run-Step "워커 이미지 교체" { kubectl set image deployment/travel-worker travel-worker=$image -n $Namespace }
    Run-Step "API 준비 확인" { kubectl rollout status deployment/travel-api -n $Namespace --timeout=10m }
    Run-Step "워커 준비 확인" { kubectl rollout status deployment/travel-worker -n $Namespace --timeout=10m }
} catch {
    Write-Host "배포 실패: 이전 이미지로 복원합니다." -ForegroundColor Red
    if ($previousApi) { kubectl set image deployment/travel-api travel-api=$previousApi -n $Namespace }
    if ($previousWorker) { kubectl set image deployment/travel-worker travel-worker=$previousWorker -n $Namespace }
    kubectl rollout status deployment/travel-api -n $Namespace --timeout=10m
    kubectl rollout status deployment/travel-worker -n $Namespace --timeout=10m
    throw
}

kubectl annotate deployment/travel-api -n $Namespace kubernetes.io/change-cause="manual deploy $Tag ($commit)" --overwrite
kubectl annotate deployment/travel-worker -n $Namespace kubernetes.io/change-cause="manual deploy $Tag ($commit)" --overwrite
kubectl get deployment travel-api travel-worker -n $Namespace -o wide

Write-Host "`n배포 완료: $image" -ForegroundColor Green
Write-Host "이전 API 이미지: $previousApi"
Write-Host "이전 워커 이미지: $previousWorker"
