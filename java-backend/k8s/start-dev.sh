#!/usr/bin/env bash
# k8s/start-dev.sh — one-command local bring-up on Rancher Desktop k3s (LOCAL_DEPLOY §3/§16/§18).
# Order: secrets -> existing PostgreSQL (yocat-pg) -> app ns/Secret/Redis -> build image ->
#        deploy API(>=2)/Service/Ingress/Flyway Job -> wait Ready -> migrate -> seed -> health.
# Idempotent; safe to re-run. Thin wrapper over the make targets so CI and local use the same commands.
set -euo pipefail
cd "$(dirname "$0")/.."            # repo outputDir root

kubectl config use-context rancher-desktop

echo "=== [1/7] generate local fake secrets ==="
./scripts/bootstrap-secrets.sh

echo "=== [2/7] ensure existing PostgreSQL (namespace yocat) ==="
make db-up

echo "=== [3/7] app namespace + Secret + Redis ==="
make k8s-init

echo "=== [4/7] build API image (nerdctl -> k8s.io) ==="
bash k8s/build.sh

echo "=== [5/7] deploy API (>=2 replicas) + Service + Ingress + Flyway Job ==="
make k8s-apply
kubectl wait --for=condition=Ready pods -l app=yocat-office-backend-api \
  -n yocat-office-backend-local --timeout=300s

echo "=== [6/7] DB migration (Flyway Job) + seed ==="
make db-migrate
make db-seed

echo "=== [7/7] health check + HA replica count ==="
make health-check
echo "api Running replicas = $(kubectl get pods -l app=yocat-office-backend-api -n yocat-office-backend-local --no-headers 2>/dev/null | grep -c Running) (need >= 2)"
echo "✅ local k8s environment ready → http://yocat-office-backend.local/health/ready"
