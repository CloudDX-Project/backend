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
