# 자유 입력(prompt) Bedrock 반영 변경

## 백엔드 변경
- `POST /api/trips`의 `TripCreateRequest`에 선택 필드 `prompt`가 추가되었습니다.
- `trips.prompt` 컬럼(최대 1000자)에 저장됩니다. (`ddl-auto: update` 환경에서는 자동 반영)
- `TripResponse.prompt`로도 반환합니다.
- 최종 일정 생성 시 `TripPlanBedrockService`가 `trip.userPrompt`로 Bedrock 입력 JSON에 전달합니다.
- 프롬프트 인젝션 방지를 위해 자유 입력은 사용자 선호 데이터로만 취급하고 기존 절대 규칙보다 우선하지 않도록 지시합니다.

## 프론트 요청에서 필요한 값
현재 프론트의 `prompt` state가 `POST /api/trips` body에 포함되어야 실제로 백엔드까지 전달됩니다.

예시:
```json
{
  "prompt": "2박 3일 도쿄 여행, 50만원 예산으로 맛집과 야경을 즐기고 싶어요."
}
```

기존 요청과의 호환을 위해 `prompt`는 선택값이며 누락/빈 문자열이어도 여행 생성은 가능합니다.
