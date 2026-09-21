# SonarCloud Quality Gate 재실행 가이드

## 코드 수정 내용

- `PlanPlaceService`의 동적 SQL 조합을 타입별 고정 SQL로 교체
- 한국 표준시를 명시한 `ScheduleTime`으로 일정 시간 차이 계산 통합
- 거리·점수 계산과 Bedrock JSON 추출을 공통 유틸리티로 통합
- 캐시, 시간, JSON, 장소 검색 단위 테스트 추가
- JaCoCo XML 경로를 Sonar Gradle 플러그인에 명시
- 프로젝트 버전을 `0.0.2`로 증가

## 로컬 검증

```powershell
.\gradlew.bat clean test jacocoTestReport
start build\reports\jacoco\test\html\index.html
```

## SonarCloud New Code 기준

기존 분석에서 신규 코드가 18,000줄 이상으로 잡혀 있었다. SonarCloud에서 다음을 1회 확인한다.

1. `Project Settings` → `New Code`
2. `main` 브랜치를 `Previous version`으로 설정
3. 수정 코드를 push하여 `0.0.2` 버전으로 재분석

이후에는 `main`에 새로 추가·수정된 코드를 기준으로 80% 커버리지를 관리한다.
