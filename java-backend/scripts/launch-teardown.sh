#!/usr/bin/env bash
# scripts/launch-teardown.sh — stop/clean the local launch (pairs with scripts/launch-local.sh).
#
# Removes the app namespace deployed by launch-local.sh and drops ONLY the yocat BC schemas that
# Flyway created in the shared yocat DB, so the launch never pollutes the existing membership DB
# (the 'public' schema is never touched). Idempotent: safe to run even if nothing is deployed.
#
# 用法: bash scripts/launch-teardown.sh
set -uo pipefail

RD_BIN="/c/Program Files/Rancher Desktop/resources/resources/win32/bin"
[[ -d "$RD_BIN" ]] && export PATH="$RD_BIN:$PATH"
export DOCKER_HOST="${DOCKER_HOST:-npipe:////./pipe/docker_engine}"

APP_NS="yocat-office-backend-local"
DB_NS="yocat"
YOCAT_SCHEMAS="member,wallet,deposit,entitlement,record,leaderboard,game_save,modulith,platform"

echo "──────── launch-teardown ────────"

# Stop any stray kubectl port-forward started for the launch (NodePort alt entrypoint).
pkill -f "kubectl port-forward -n ${APP_NS}" 2>/dev/null && echo "stopped port-forward(s)" || echo "no port-forward running"

# Delete the app namespace (keeps existing yocat-pg DB + PVC intact).
kubectl delete namespace "${APP_NS}" --ignore-not-found --timeout=120s 2>&1 | tail -2 || true

# Drop only the yocat BC schemas we added; the membership 'public' schema is never touched.
kubectl exec -n "${DB_NS}" deploy/yocat-pg -- psql -U yocat -d yocat -q -c \
  "DROP SCHEMA IF EXISTS ${YOCAT_SCHEMAS} CASCADE;" 2>&1 | tail -2 || true

kubectl get ns "${APP_NS}" >/dev/null 2>&1 && echo "   (namespace still terminating)" || echo "✅ namespace removed"
echo "✅ launch-teardown done (existing yocat-pg DB rows preserved; membership public schema untouched)"
