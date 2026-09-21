#!/bin/bash

set -e

NAMESPACE="monitoring"
RELEASE_NAME="kube-prometheus-stack"

echo "[1/2] Uninstall kube-prometheus-stack"

helm uninstall \
  "$RELEASE_NAME" \
  --namespace "$NAMESPACE"

echo "[2/2] Check namespace"

kubectl get pods -n "$NAMESPACE" || true

echo "Prometheus / Grafana stack removed."
echo "Metrics Server remains installed."
