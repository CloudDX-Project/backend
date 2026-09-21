# 항공 시각·지도 경로 수정 안내

## 확인한 현상

최신 첨부 response_1789618810918.json은 전체 계획이 아니라 3일차 이동 구간 목록입니다.
이 응답에서 제주공항 도착은 19:44인데 귀국편은 17:00 출발, 18:15 도착입니다.
로컬 7구간은 모두 path가 비어 있으며 6개는 ESTIMATED, 셔틀은 FIXED입니다.
첫날의 구체적인 실행 결과는 이 JSON에 없으므로 첫날 수정은 소스의 시간 처리 흐름을 기준으로 했습니다.

## 원인과 변경

- RedisConfig: 기본 CACHE_PROVIDER=simple의 고정 캐시 목록에 kakaoDrivingRoute가 누락되어 있습니다. 이 설정에서는 @Cacheable 처리 단계에서 예외가 발생해 실제 API를 부르기 전에 추정 경로로 대체됩니다. 해당 캐시를 등록했습니다. 운영 환경의 최종 원인은 기존 로그의 'Kakao Mobility 길찾기 실패' 뒤 오류 메시지로 확인할 수 있습니다.
- TripPlanService: 첫날 공항 준비 시작을 Trip.startTime까지 잘라내던 처리를 제거했습니다. 출발 시각과 공항 준비 시작 시각은 서로 다른 값입니다.
- 마지막 날에는 공항 도착 마감 검사를 추가했습니다. 이동·반납·셔틀을 포함하여 늦으면 뒤쪽 관광/카페/식사를 제거합니다. 이른 항공편은 체크아웃을 앞당깁니다. 고정된 항공 연결 자체가 불가능하면 잘못된 일정을 저장하지 않고 오류를 반환합니다.
- 시간표와 TransportSegment가 같은 경로 조회 결과와 동일한 추정식을 사용합니다. 기존에는 시간표의 추정 속도 35km/h와 이동 구간의 45km/h가 달랐습니다.
- RoutingService: 일정 생성 1회 안에서 동일 좌표의 성공/실패 조회 결과를 재사용하고 종료 시 해제합니다. 시간표와 경로 저장 사이에서 결과가 달라지거나 같은 API를 반복 호출하는 문제를 줄였습니다.
- TripDayResponse: 저장된 여행 조회에도 transportSegments 및 path를 반환합니다. 기존에는 생성 응답에만 포함되었습니다.
- 프론트: 공항 준비 카드와 FLIGHT 카드를 분리하여 목록·시간표가 모두 최종 startAt을 사용합니다. 오는 편 도착 공항 카드가 출발 시각으로 배치되던 문제를 수정했습니다.
- RouteMap: 실제 도로 좌표는 실선, path가 없거나 추정값인 구간은 출발·도착 좌표를 잇는 점선으로 표시합니다. '추정 연결선'을 명시하며 항공 구간은 현지 지도에서 제외합니다. 점선은 실제 주행 가능한 도로 경로를 의미하지 않습니다.
- 이전 식사 슬롯/첫날 체크인/마지막 날 후보 보정 서비스도 이번 첨부 소스에 통합했습니다. 다른 항공 가격·외부 API·설정 변경은 첨부 최신 소스를 유지합니다.

## 시간 기준

모든 API 날짜/시각은 기존 국내 여행 모델의 한국 현지 시각(LocalDateTime)을 유지합니다.

| 항목 | 기준 |
|---|---|
| 항공 출발·도착 | 선택한 FlightCandidate의 시각을 고정 |
| 가는 편 공항 준비 시작 | 이륙 90분 전. Trip.startTime으로 잘라내지 않음 |
| 착륙 후 하차·수하물 | 기본 30분 |
| 렌터카 인수 | 업체 도착 후 기본 20분 |
| 렌터카 반납 | 업체 도착 후 기본 20분 |
| 셔틀 | 선택한 업체의 estimatedShuttleMinutes |
| 오는 편 공항 마감 | 이륙 90분 전까지 도착 |
| 마지막 날 공항 카드 startAt | 계산된 실제 도착 시각. 마감보다 일찍 오면 대기 시간이 늘어남 |
| Trip.startTime / endTime | 기존 항공 검색·선택 범위의 하한/상한. 출발지→공항 이동은 별도로 모델링하지 않음 |

30분/20분/90분은 일정 계산용 기본값이며 항공사 규정이나 실시간 대기시간을 뜻하지 않습니다.
변경 위치: backend-main/src/main/java/com/travel/trip/plan/service/FlightTimePolicy.java.
정책 값을 바꾸면 Bedrock 프롬프트의 대응 안내도 함께 맞춰주세요.

예: 17:00 귀국편이면 공항 마감은 15:30입니다. 마지막 장소 종료 + 업체까지 이동 + 반납20분 + 셔틀8분 <= 15:30인 일정만 남깁니다.

## 적용

1. 백엔드와 프론트의 각 ZIP을 대응 프로젝트에 적용합니다. 두 ZIP은 전체 소스이며 루트 폴더 이름은 backend-main / frontend-master입니다.
2. 백엔드 재시작 후 새 일정을 생성합니다. 기존 DB에 저장된 잘못된 시간/빈 path를 자동으로 재계산하지는 않습니다. 기존 여행 ID에는 POST /api/trips/{tripId}/plan으로 다시 생성할 수 있습니다.
3. 브라우저를 새로고침한 뒤 지도와 시간표를 확인합니다. PWA의 이전 버전이 남아 있으면 새 버전 적용/재로드 후 확인합니다.
4. 정상 조회 시 transportSegments[].routeProvider는 KAKAO_MOBILITY 계열이며 path에 도로 좌표가 포함됩니다. 계속 ESTIMATED/FIXED이면 서버 로그의 정확한 오류를 확인합니다. 키·권한·쿼터·네트워크 오류는 이 수정만으로 해결된다고 단정할 수 없습니다.
5. GET /api/trips/{id}로 다시 읽어도 days[].transportSegments[].path가 유지되는지 확인합니다.

## 검증

이 환경에서 수행한 검증:

- 프론트 Node 테스트 21개 통과(기존 13개 + 신규 8개).
- 변경된 JS/JSX 파일 7개 구문 파싱 통과.
- 변경된 Java 파일 13개 구문 파싱 통과. 의존성을 포함한 전체 타입 컴파일은 아님.
- FlightTimePolicy는 Java 컴파일 및 실제 17:00 항공 마감 경계값 4개 검증 통과.
- ZIP 내 기존 파일 보존 및 압축 무결성 확인.

실행하지 못한 검증:

- Spring/JUnit 테스트: Gradle 배포본 다운로드가 네트워크 차단으로 실패했습니다. 전체 백엔드 컴파일/통합 실행은 확인하지 못했습니다.
- Vite 전체 빌드: 현재 환경에 프로젝트 의존성이 설치되어 있지 않아 실행할 수 없었습니다.
- 실제 Bedrock/Kakao/DB와 브라우저 지도를 연결한 통합 동작은 사용자 실행 환경에서 확인이 필요합니다.

로컬 PowerShell에서 프로젝트별로 실행:

```powershell
# 백엔드 프로젝트 / Java 21
.\gradlew.bat test --tests "com.travel.trip.plan.service.*" --tests "com.travel.routing.service.*" --tests "com.travel.trip.dto.*" --tests "com.travel.global.config.RoutingCacheConfigTest"

# 프론트 프로젝트
node --test tests/planTimingAndRoutes.test.mjs
npm run build
```

우도 등 도선이 필요한 구간의 교통 모델 확장은 포함하지 않았습니다. 도로 조회가 실패한 점선을 실제 주행 가능 경로로 해석하지 마세요.
