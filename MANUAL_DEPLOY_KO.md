# TripBuddy 백엔드 수동 배포

## 준비

- Docker Desktop을 실행합니다.
- AWS CLI가 `782913119640` 계정에 로그인되어 있어야 합니다.
- `kubectl`이 설치되어 있어야 합니다.
- 백엔드의 `.env.local`은 로컬 실행에만 사용하며 이미지에 포함되지 않습니다.

## 배포

백엔드 폴더의 PowerShell 터미널에서 실행합니다.

```powershell
.\scripts\deploy-manual.ps1
```

스크립트가 다음 작업을 순서대로 수행합니다.

1. 전체 테스트와 배포용 JAR 생성
2. 고유 태그의 Docker 이미지 생성
3. ECR 로그인과 이미지 업로드
4. EKS 연결
5. `travel-api`와 `travel-worker`를 같은 이미지로 교체
6. 모든 파드의 준비 상태 확인
7. 실패하면 직전 API·워커 이미지로 자동 복원

일정 생성은 API와 워커가 함께 처리하므로 둘 중 하나만 배포하면 안 됩니다.

## 로컬 실행

VS Code의 실행 및 디버그 화면에서 `TripBuddy Backend (local)`을 선택합니다.

터미널에서는 다음 명령을 사용할 수 있습니다.

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

정상 실행 기준은 마지막 로그에 `Started TravelApplication`이 표시되고 다음 주소가 `UP`을 반환하는 것입니다.

```text
http://localhost:8080/actuator/health
```
