# Monitoring

AI Travel Planner Backend의 EKS 및 AWS Monitoring 구성입니다.

## Monitoring Strategy

Kubernetes 환경과 AWS Managed Service를 분리하여 모니터링합니다.

```text
EKS Node / Pod / Application
        │
        ├── Metrics Server
        │
        └── Prometheus
               ↓
            Grafana

AWS Managed Services
        │
        └── Amazon CloudWatch
```

---

## 1. Metrics Server

Amazon EKS Cluster에 Metrics Server를 설치하고
Node / Pod Resource Metric 수집을 검증했습니다.

### Node Metric

```bash
kubectl top node
```

### Pod Metric

```bash
kubectl top pod -n travel
```

Metrics Server는 현재 Cluster에 유지하고 있습니다.

---

## 2. Prometheus / Grafana

`kube-prometheus-stack`을 이용해 Prometheus와 Grafana를
EKS Cluster에 설치하고 Monitoring 동작을 검증했습니다.

검증 대상:

- Kubernetes Node
- Kubernetes Pod
- Backend Application Workload

### 상태 확인

```bash
kubectl get pods -n monitoring
```

### Grafana Local Access

```bash
kubectl port-forward \
  svc/kube-prometheus-stack-grafana \
  3000:80 \
  -n monitoring
```

접속:

```text
http://localhost:3000
```

---

## 3. Resource Optimization

개발 환경의 EKS Worker Node Resource를 절약하기 위해
Prometheus / Grafana Stack은 상시 운영하지 않습니다.

기능 검증 완료 후 제거했습니다.

```bash
helm uninstall kube-prometheus-stack \
  -n monitoring
```

필요할 때 재설치하여 사용하는 방식으로 운영합니다.

Metrics Server는 계속 유지합니다.

---

## 4. Current Status

| Component | Status |
|---|---|
| Metrics Server | Running |
| `kubectl top` | Verified |
| Prometheus | Verified / Currently Removed |
| Grafana | Verified / Currently Removed |
| CloudWatch | Verified |

---

## 5. Monitoring Scope

### Kubernetes

Prometheus / Grafana:

- Node Resource
- Pod Resource
- Backend Workload

### AWS

Amazon CloudWatch:

- Application Load Balancer
- Amazon RDS
- ElastiCache Redis
- Amazon SQS
