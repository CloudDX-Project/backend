# Amazon CloudWatch Monitoring

AI Travel Planner에서 AWS Managed Service의 상태와 Metric은
Amazon CloudWatch를 사용하여 확인합니다.

## Monitoring Architecture

```text
ALB
RDS
ElastiCache Redis
SQS
 │
 ↓
Amazon CloudWatch
```

---

## Application Load Balancer

주요 확인 Metric:

- RequestCount
- TargetResponseTime
- HTTPCode_Target_5XX_Count
- HealthyHostCount

확인 목적:

- Backend 요청량 확인
- Target 응답 지연 확인
- HTTP 5xx 발생 확인
- ALB Target 상태 확인

---

## Amazon RDS

주요 확인 Metric:

- CPUUtilization
- DatabaseConnections
- FreeStorageSpace

확인 목적:

- DB CPU 상태
- Connection 상태
- Storage 사용 상태

---

## ElastiCache Redis

주요 확인 Metric:

- CPUUtilization
- DatabaseMemoryUsagePercentage
- CurrConnections

확인 목적:

- Redis CPU 사용량
- Memory 사용률
- Connection 상태

---

## Amazon SQS

주요 확인 Metric:

- NumberOfMessagesSent
- NumberOfMessagesReceived
- ApproximateNumberOfMessagesVisible

확인 목적:

- API Producer 메시지 전송 확인
- Worker 메시지 소비 확인
- Queue 적체 상태 확인

---

## Monitoring Policy

Kubernetes Workload:

```text
Prometheus + Grafana
```

AWS Managed Service:

```text
Amazon CloudWatch
```

각 환경의 특성에 맞게 Monitoring Tool을 분리하여 사용합니다.
