# SonarQube Cloud 신규 코드 커버리지 보강

## 변경 내용

- 관광지 추천 서비스의 정량 점수 및 Bedrock 재정렬 경로 테스트
- 카페 후보 계산, 추천 재정렬, 검색 거리 계산 테스트
- 음식점 추천 재정렬 및 검색 거리 계산 테스트
- Bedrock JSON의 null, blank, 잘못된 중괄호 순서 분기 테스트

외부 DB, Redis, SQS, Bedrock API를 실제로 호출하지 않고 Mockito로 격리한 단위 테스트입니다.

## 로컬 확인

Windows PowerShell에서 프로젝트 루트에서 실행합니다.

```powershell
.\gradlew.bat clean test jacocoTestReport
```

테스트 결과:

```text
build\reports\tests\test\index.html
```

JaCoCo 결과:

```text
build\reports\jacoco\test\html\index.html
build\reports\jacoco\test\jacocoTestReport.xml
```

## Git 반영

원격 이력을 먼저 받아온 뒤 현재 브랜치가 `main`인지 확인합니다.

```powershell
git fetch origin
git branch --show-current
git status --short
git diff --stat origin/main
```

테스트 파일과 안내 문서만 추가합니다.

```powershell
git add src/test/java/com/travel/attraction/AttractionRecommendationServiceTest.java
git add src/test/java/com/travel/cafe/CafeServicesTest.java
git add src/test/java/com/travel/restaurant/RestaurantServicesTest.java
git add src/test/java/com/travel/global/util/BedrockJsonTest.java
git add SONAR_COVERAGE_FIX_KO.md

git diff --cached --stat
git commit -m "test: improve recommendation service coverage"
git pull --rebase origin main
git push origin main
```

`git pull --rebase`에서 충돌이 발생하면 강제 푸시하지 말고 충돌 파일을 해결한 후 다음을 실행합니다.

```powershell
git add <충돌을 해결한 파일>
git rebase --continue
git push origin main
```


## 2026-09-22 최종 배포 전 보강

이번 최종본에서는 새 코드가 SonarQube 신규 코드 커버리지에서 불리하지 않도록 아래 테스트를 추가했습니다.

- `TripPromptDayConstraintParserTest`
  - 기존 CI가 호출하던 `requestedDayFor(String, String, int)` 호환 API 복구
  - 관광지→일차, 일차→관광지, 시간만 있는 프롬프트, 다중 관광지, 범위 밖 일차 검증
- `FuelCostServiceTest`
  - Opinet 정상 가격 계산
  - 연료/연비 기본값
  - 전기차/0km 처리
  - Opinet 장애·null·0 응답 시 fallback 단가
- `OpinetFuelPriceClientTest`
  - 휘발유/경유/LPG JSON 파싱
  - API 키 누락
  - 빈 응답, 잘못된 가격, HTTP 오류
- `VehicleFuelTypeTest`
  - Opinet 제품 코드와 전기차 제외 분기

CI의 Build 단계는 다음처럼 JaCoCo XML 생성까지 명시적으로 수행합니다.

```bash
./gradlew clean build jacocoTestReport --no-daemon
```

Sonar 단계는 기존 `build/reports/jacoco/test/jacocoTestReport.xml`을 그대로 읽습니다.
