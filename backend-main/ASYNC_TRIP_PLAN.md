# 비동기 여행 일정 생성

여행 일정 생성은 API Pod가 Bedrock을 직접 호출하지 않고 아래 흐름으로 처리한다.

1. `POST /api/trips/{tripId}/plan`
2. API가 `trip_plan_generations`에 `PENDING` 작업 저장
3. API가 SQS에 `requestId`, `userId`, `tripId` 전송
4. Worker가 메시지를 수신하고 상태를 `PROCESSING`으로 변경
5. Worker가 기존 `TripPlanService`를 실행해 Bedrock과 Kakao Mobility를 호출
6. 일정과 이동 구간을 기존 테이블에 저장하고 작업을 `COMPLETED`로 변경
7. 프론트가 `GET /api/trips/{tripId}/plan`을 polling해 완료 결과 표시

## 런타임 구분

같은 Docker 이미지를 사용하고 환경변수로 역할을 구분한다.

```text
API Pod:    APP_RUNTIME_ROLE=api
Worker Pod: APP_RUNTIME_ROLE=worker
```

`BedrockClient`는 `worker` 프로세스가 아니면 실제 모델 호출을 거부한다.

## 필수 환경변수

```text
TRIP_PLAN_QUEUE_URL=https://sqs.ap-northeast-2.amazonaws.com/{accountId}/{queueName}
AWS_SQS_REGION=ap-northeast-2
AWS_BEDROCK_REGION=ap-northeast-2
BEDROCK_MODEL_ID=apac.amazon.nova-lite-v1:0
```

선택 환경변수의 기본값은 다음과 같다.

```text
TRIP_PLAN_QUEUE_WAIT_TIME_SECONDS=20
TRIP_PLAN_QUEUE_VISIBILITY_TIMEOUT_SECONDS=900
TRIP_PLAN_QUEUE_POLL_DELAY_MS=1000
```

## IAM

API ServiceAccount Role:

```text
sqs:SendMessage
```

Worker ServiceAccount Role:

```text
sqs:ReceiveMessage
sqs:DeleteMessage
sqs:ChangeMessageVisibility
sqs:GetQueueAttributes
bedrock:InvokeModel
```

Worker 정책의 Bedrock 리소스와 `BEDROCK_MODEL_ID`는 반드시 같은 inference profile을 가리켜야 한다.

## 배포

`k8s/configmap.yaml`의 `TRIP_PLAN_QUEUE_URL`을 실제 Queue URL로 변경한다.

```powershell
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/worker-deployment.yaml
```

`travel-api-sa`와 `ai-travel-worker-sa`가 실제 EKS ServiceAccount 이름과 다르면 각 Deployment의 `serviceAccountName`을 실제 이름으로 변경한다.

## 로컬 실행

로컬에서도 API와 Worker를 별도 프로세스로 실행한다. 두 프로세스는 같은 DB와 Redis, SQS Queue를 사용해야 한다.

API 터미널:

```powershell
$env:APP_RUNTIME_ROLE="api"
$env:TRIP_PLAN_QUEUE_URL="실제 Queue URL"
.\gradlew.bat bootRun --args='--spring.profiles.active=local'
```

Worker 터미널:

```powershell
$env:APP_RUNTIME_ROLE="worker"
$env:SPRING_MAIN_WEB_APPLICATION_TYPE="none"
$env:TRIP_PLAN_QUEUE_URL="실제 Queue URL"
$env:BEDROCK_MODEL_ID="apac.amazon.nova-lite-v1:0"
.\gradlew.bat bootRun --args='--spring.profiles.active=local'
```
