# 오피넷 유류비 연동

## 1. 환경변수

로컬 `backend-main/.env.local` 또는 배포 Secret에 아래 값을 추가합니다.

```properties
OPINET_API_KEY=발급받은_오피넷_API_KEY
```

EKS에서는 `travel-backend-secret`에 `OPINET_API_KEY` 키를 추가하면 API/Worker가 `envFrom`으로 함께 읽습니다.

예시:

```powershell
kubectl create secret generic travel-backend-secret `
  -n travel `
  --from-literal=OPINET_API_KEY='발급키' `
  --dry-run=client -o yaml | kubectl apply -f -
```

기존 Secret에 DB/JWT 등 다른 값이 들어 있다면 위 명령으로 통째로 덮어쓰지 말고 기존 Secret 관리 방식에 맞춰 OPINET_API_KEY만 추가하세요.

## 2. 사용하는 API

- `GET https://www.opinet.co.kr/api/avgAllPrice.do`
- `out=json`
- `certkey=${OPINET_API_KEY}`

제품 코드:

- `B027`: 휘발유
- `D047`: 자동차용 경유
- `K015`: 자동차용 부탄(LPG)

전기차는 오피넷 유류비 대상이 아니므로 유류비를 0원으로 계산합니다.

## 3. 계산식

자동차/렌터카 이동 구간마다 Kakao Mobility 실제 도로 거리(km)를 사용합니다.

```text
유류비 = (도로거리 km / 차량 연비 km/L) × 오피넷 전국 평균 유가 원/L
이동구간 비용 = 유류비 + Kakao Mobility 통행료
```

오피넷 호출 결과는 1시간 캐시합니다. API 일시 장애나 키 누락 시 여행 일정 생성 자체가 실패하지 않도록 기존 fallback 단가를 사용합니다.

## 4. Trip 생성 요청 추가 필드

```json
{
  "fuelType": "GASOLINE",
  "vehicleEfficiencyKmpl": 12.5
}
```

지원 값:

- `GASOLINE`
- `DIESEL`
- `LPG`
- `ELECTRIC`

프론트에서는 자가용의 차종/연료 선택과 렌터카 차종을 기준으로 자동 전송합니다.

## 5. 확인 방법

일정 생성 후 `days[].transportSegments[]`에서 자동차 구간의 `cost`를 확인합니다. 이 값은 오피넷 유류비와 Kakao 통행료가 합산된 값입니다.

또한 `GET /api/trips/{tripId}/cost`의 `transportCost`는 저장된 이동 구간 비용 합계이므로 오피넷 유류비가 반영됩니다.
