# Bedrock 일정 생성 품질 보정

## 변경 내용

- 최종 Planner가 요청한 `maxTokens`와 `temperature`를 AWS Bedrock 호출에 실제로 반영합니다.
- 관광지/음식점/카페 후보별 Bedrock 재정렬은 기본적으로 생략합니다.
  - 후보는 기존 백엔드 정량 점수로 정렬됩니다.
  - 최종 일정 Planner의 Bedrock 호출은 계속 Worker에서 실행됩니다.
- 좌표 간 거리가 5m 이하이면 Kakao Mobility 길찾기를 호출하지 않습니다.
- 같은 숙소가 연속 배치되면 하나만 남깁니다.
  - `애월더선셋 리조트`와 `애월더선셋리조트`처럼 공백/기호만 다른 이름도 같은 장소로 봅니다.
  - 동일 `placeId` 또는 5m 이내 좌표도 같은 숙소로 봅니다.

## 환경변수

기본 설정은 후보 재정렬을 사용하지 않습니다.

```properties
BEDROCK_RERANK_ENABLED=false
```

후보별 Bedrock 재정렬까지 다시 사용하려면 Worker에만 다음 값을 설정합니다.

```properties
BEDROCK_RERANK_ENABLED=true
```

## 로컬 검증

```powershell
.\gradlew.bat clean test
.\gradlew.bat clean build
```

## 배포 확인

새 이미지를 ECR에 올린 뒤 API와 Worker가 같은 새 digest를 사용하는지 확인합니다.

```powershell
kubectl get pods -n travel -l app=travel-api `
  -o custom-columns="NAME:.metadata.name,IMAGE_ID:.status.containerStatuses[0].imageID"

kubectl get pods -n travel -l app=travel-worker `
  -o custom-columns="NAME:.metadata.name,IMAGE_ID:.status.containerStatuses[0].imageID"
```
