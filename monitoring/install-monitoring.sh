#!/bin/bash

set -e

NAMESPACE="monitoring"
RELEASE_NAME="kube-prometheus-stack"
CHART="prometheus-community/kube-prometheus-stack"
CHART_VERSION="91.4.1"

echo "[1/3] Add Prometheus Helm repository"

helm repo add prometheus-community \
  https://prometheus-community.github.io/helm-charts

helm repo update

echo "[2/3] Install kube-prometheus-stack"

helm upgrade --install \
  "$RELEASE_NAME" \
  "$CHART" \
  --namespace "$NAMESPACE" \
  --create-namespace \
  --version "$CHART_VERSION"

echo "[3/3] Check monitoring pods"

kubectl get pods -n "$NAMESPACE"

echo "Monitoring stack installation completed."
