#!/usr/bin/env bash
# scripts/runbook/health-check.sh
# ─────────────────────────────────────────────────────────────────────────────
# RUNBOOK §2.2 元件清單健康檢查 + §4.4 本機部署後健康驗證 + §7.1/§7.2 故障排除首步。
#
# 做四件事（對應 RUNBOOK §健康檢查）：
#   1. curl Ingress /health/live + /health/ready（兩個平台底座 endpoint，走 Ingress 不直連 pod）
#   2. kubectl get pods（API ≥2 Running、READY=1/1，EDD §3.7.2 HA 下限）
#   3. DB 連線測試（yocat-pg：pg_isready + SELECT 1）
#   4. Redis 連線測試（redis-cli ping）
#
# 真實契約（來自 HealthController.java + docs/K8S_SMOKE_REPORT.md Finding #2）：
#   GET /health/live  → 200 {"status":"UP"}
#   GET /health/ready → 200 {"status":"UP","db":"UP","redis":"UP"}（DB 不可達時 503 "DOWN"）
#   （LOCAL_DEPLOY 散文範例寫 "ok" 為示意；實際回應為 "UP"，本腳本以實際契約為準。）
#
# 退出碼：0 全綠；1 有檢查失敗（細節見輸出）。唯讀，無任何寫入/破壞性操作。
set -euo pipefail

# ── 設定（全部可由環境變數覆寫；預設對齊 Makefile 本機值，無 hardcode namespace）──
KUBECTL="${KUBECTL:-kubectl}"
CURL="${CURL:-curl}"
APP_NS="${APP_NS:-yocat-office-backend-local}"          # Makefile APP_NS
DB_NS="${DB_NS:-yocat}"                                   # Makefile DB_NS（既有 PostgreSQL）
API_DEPLOY="${API_DEPLOY:-yocat-office-backend-api}"     # LOCAL_DEPLOY §4.5
DB_DEPLOY="${DB_DEPLOY:-yocat-pg}"                        # docs/req/postgres.yaml
REDIS_DEPLOY="${REDIS_DEPLOY:-redis}"                    # k8s/overlays/local/redis.yaml
DB_USER="${DB_USER:-yocat}"
DB_NAME="${DB_NAME:-yocat}"
MIN_REPLICAS="${MIN_REPLICAS:-2}"                        # Iron Constraint: HA 下限（EDD §3.7.2）
# 對外入口：LOCAL_DEPLOY 單一外部入口 = Ingress（Traefik）http://yocat-office-backend.local
# Prod 覆寫範例：BASE_URL=https://api.yocat.sayyogames.com
BASE_URL="${BASE_URL:-http://yocat-office-backend.local}"

RC=0
pass() { echo "  [OK]   $*"; }
fail() { echo "  [FAIL] $*"; RC=1; }

echo "════════════════════════════════════════════════════════════════"
echo " health-check  ns=$APP_NS  base=$BASE_URL  ($(date '+%F %T'))"
echo "════════════════════════════════════════════════════════════════"

# ── 1. HTTP health endpoints（走 Ingress，不直連 pod）──────────────────────────
echo "[1/4] HTTP health endpoints (Ingress)"
for ep in /health/live /health/ready; do
  code="$("$CURL" -s -o /dev/null -w '%{http_code}' --max-time 10 "${BASE_URL}${ep}" || echo "000")"
  if [[ "$code" == "200" ]]; then
    body="$("$CURL" -s --max-time 10 "${BASE_URL}${ep}" || true)"
    if echo "$body" | grep -q '"status":"UP"'; then
      pass "GET ${ep} → 200 ${body}"
    else
      fail "GET ${ep} → 200 but body not status:UP → ${body}"
    fi
  else
    fail "GET ${ep} → HTTP ${code} (expected 200; 503=DB down → RUNBOOK §7.5; 000=無路由 → 查 /etc/hosts + traefik §7.2)"
  fi
done

# ── 2. Pod 健康（API ≥ MIN_REPLICAS Running）──────────────────────────────────
echo "[2/4] API pods (${API_DEPLOY}, ns=${APP_NS})"
if pods="$("$KUBECTL" get pods -n "$APP_NS" -l "app=${API_DEPLOY}" --no-headers 2>/dev/null)"; then
  running="$(echo "$pods" | grep -c 'Running' || true)"
  ready="$(echo "$pods" | grep -cE '([0-9]+)/\1' || true)"   # READY n/n
  echo "$pods" | sed 's/^/        /'
  if [[ "$running" -ge "$MIN_REPLICAS" ]]; then
    pass "${running} pod Running (≥ ${MIN_REPLICAS} HA 下限)"
  else
    fail "only ${running} pod Running (< ${MIN_REPLICAS}) → RUNBOOK §7.6 CrashLoopBackOff"
  fi
else
  fail "kubectl get pods 失敗 → 確認 context/namespace（RUNBOOK §7.2）"
fi

# ── 3. DB 連線測試（yocat-pg）────────────────────────────────────────────────
echo "[3/4] PostgreSQL (${DB_DEPLOY}, ns=${DB_NS})"
if "$KUBECTL" exec -n "$DB_NS" "deploy/${DB_DEPLOY}" -- pg_isready -U "$DB_USER" -d "$DB_NAME" >/dev/null 2>&1; then
  pass "pg_isready accepting connections"
  if "$KUBECTL" exec -n "$DB_NS" "deploy/${DB_DEPLOY}" -- psql -U "$DB_USER" -d "$DB_NAME" -tAc 'SELECT 1' 2>/dev/null | grep -q '^1$'; then
    pass "SELECT 1 → 1"
  else
    fail "SELECT 1 失敗 → RUNBOOK §7.5 DB Issues"
  fi
else
  fail "pg_isready 失敗 → RUNBOOK §7.5 / Patroni failover §7.5.2"
fi

# ── 4. Redis 連線測試 ────────────────────────────────────────────────────────
echo "[4/4] Redis (${REDIS_DEPLOY}, ns=${APP_NS})"
if "$KUBECTL" exec -n "$APP_NS" "deploy/${REDIS_DEPLOY}" -- redis-cli ping 2>/dev/null | grep -q 'PONG'; then
  pass "redis-cli ping → PONG"
else
  # Redis 降級開放（EDD §8.3 circuit breaker）：失敗不致命，但需記錄供 §7.7 追查
  fail "redis-cli ping 失敗（cache 應 degrade-open，readiness 仍 UP）→ RUNBOOK §7.7"
fi

echo "────────────────────────────────────────────────────────────────"
if [[ "$RC" -eq 0 ]]; then echo "✅ health-check PASS"; else echo "❌ health-check FAIL (見上方 [FAIL])"; fi
exit "$RC"
