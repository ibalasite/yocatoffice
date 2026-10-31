#!/usr/bin/env bash
# scripts/runbook/deploy-rollback.sh
# ─────────────────────────────────────────────────────────────────────────────
# RUNBOOK §9.1 應用版本回滾（Deployment Rollback）+ §9.4 回滾後驗證（縮版）。
#
# 對應 RUNBOOK §回滾程序：kubectl rollout undo（可選 --to-revision），逐步驗證：
#   0. 確認 context（避免在錯誤叢集操作）
#   1. rollout history（記錄前一 healthy revision）
#   2. rollout undo（回前一版，或 TO_REVISION 指定版）
#   3. rollout status（等滾動完成）
#   4. pods ≥ MIN_REPLICAS Running（HA 下限）
#   5. health/ready 回 200 status:UP（真實契約）
#   6. 錢包對帳快檢（§7.8：wallet 餘額 == Σledger delta，0 不一致才算完成）
#
# 用法：
#   bash scripts/runbook/deploy-rollback.sh              # 回前一 revision
#   TO_REVISION=7 bash scripts/runbook/deploy-rollback.sh # 回指定 revision
#   DRY_RUN=1 bash scripts/runbook/deploy-rollback.sh     # 只印將執行的步驟，不實際 undo
#
# 退出碼：0 回滾並驗證通過；1 任一步失敗（需依 RUNBOOK 對應章節人工處置）。
set -euo pipefail

KUBECTL="${KUBECTL:-kubectl}"
CURL="${CURL:-curl}"
APP_NS="${APP_NS:-yocat-office-backend-local}"
DB_NS="${DB_NS:-yocat}"
API_DEPLOY="${API_DEPLOY:-yocat-office-backend-api}"
DB_DEPLOY="${DB_DEPLOY:-yocat-pg}"
DB_USER="${DB_USER:-yocat}"
DB_NAME="${DB_NAME:-yocat}"
MIN_REPLICAS="${MIN_REPLICAS:-2}"
BASE_URL="${BASE_URL:-http://yocat-office-backend.local}"
ROLLOUT_TIMEOUT="${ROLLOUT_TIMEOUT:-300s}"
# 允許限定操作 context（RUNBOOK §9.1 Step 0；空值 = 不檢查，用當前 context）
EXPECT_CONTEXT="${EXPECT_CONTEXT:-}"
TO_REVISION="${TO_REVISION:-}"
DRY_RUN="${DRY_RUN:-0}"

echo "════════════════════════════════════════════════════════════════"
echo " deploy-rollback  ns=$APP_NS  deploy=$API_DEPLOY  ($(date '+%F %T'))"
echo "════════════════════════════════════════════════════════════════"

# ── 0. Context 確認（RUNBOOK §9.1 Step 0）────────────────────────────────────
ctx="$("$KUBECTL" config current-context 2>/dev/null || echo 'unknown')"
echo "[0/6] current-context = ${ctx}"
if [[ -n "$EXPECT_CONTEXT" && "$ctx" != "$EXPECT_CONTEXT" ]]; then
  echo "❌ context 非預期 '${EXPECT_CONTEXT}' → 先 kubectl config use-context ${EXPECT_CONTEXT}；絕不在錯誤 context 操作。"
  exit 1
fi

# ── 1. Rollout history ───────────────────────────────────────────────────────
echo "[1/6] rollout history"
"$KUBECTL" rollout history "deployment/${API_DEPLOY}" -n "$APP_NS" | sed 's/^/        /'

# ── 2. Rollout undo ──────────────────────────────────────────────────────────
if [[ -n "$TO_REVISION" ]]; then
  UNDO_ARGS=(rollout undo "deployment/${API_DEPLOY}" -n "$APP_NS" --to-revision="$TO_REVISION")
else
  UNDO_ARGS=(rollout undo "deployment/${API_DEPLOY}" -n "$APP_NS")
fi
echo "[2/6] ${KUBECTL} ${UNDO_ARGS[*]}"
if [[ "$DRY_RUN" == "1" ]]; then
  echo "        (DRY_RUN=1：略過實際 undo 與後續驗證)"
  exit 0
fi
"$KUBECTL" "${UNDO_ARGS[@]}"

# ── 3. Rollout status ────────────────────────────────────────────────────────
echo "[3/6] rollout status (timeout=${ROLLOUT_TIMEOUT})"
if ! "$KUBECTL" rollout status "deployment/${API_DEPLOY}" -n "$APP_NS" --timeout="$ROLLOUT_TIMEOUT"; then
  echo "❌ rollout 未完成 → kubectl describe deployment/${API_DEPLOY} -n ${APP_NS} 查 Warning（RUNBOOK §7.6）"
  exit 1
fi

# ── 4. Pods ≥ MIN_REPLICAS ───────────────────────────────────────────────────
echo "[4/6] API pods ≥ ${MIN_REPLICAS} Running"
running="$("$KUBECTL" get pods -n "$APP_NS" -l "app=${API_DEPLOY}" --no-headers 2>/dev/null | grep -c 'Running' || true)"
if [[ "$running" -ge "$MIN_REPLICAS" ]]; then
  echo "        [OK] ${running} Running"
else
  echo "❌ 僅 ${running} Running (< ${MIN_REPLICAS}) → RUNBOOK §7.6"
  exit 1
fi

# ── 5. health/ready ──────────────────────────────────────────────────────────
echo "[5/6] health/ready"
code="$("$CURL" -s -o /dev/null -w '%{http_code}' --max-time 10 "${BASE_URL}/health/ready" || echo '000')"
body="$("$CURL" -s --max-time 10 "${BASE_URL}/health/ready" || true)"
if [[ "$code" == "200" ]] && echo "$body" | grep -q '"status":"UP"'; then
  echo "        [OK] 200 ${body}"
else
  echo "❌ health/ready HTTP ${code} ${body} → 依 db/redis fail 項分流 RUNBOOK §7.5 / §7.7"
  exit 1
fi

# ── 6. 錢包對帳快檢（§7.8；0 rows 不一致才算完成）────────────────────────────
echo "[6/6] 錢包對帳（wallet 餘額 == Σ ledger delta）"
mismatch="$("$KUBECTL" exec -n "$DB_NS" "deploy/${DB_DEPLOY}" -- psql -U "$DB_USER" -d "$DB_NAME" -tAc \
  "SELECT count(*) FROM wallet.wallets w JOIN (
     SELECT account_id,
            SUM(CASE WHEN currency='cash' THEN delta ELSE 0 END) cash_sum,
            SUM(CASE WHEN currency='gems' THEN delta ELSE 0 END) gems_sum
     FROM wallet.wallet_ledger GROUP BY account_id) l ON l.account_id=w.account_id
   WHERE w.cash <> l.cash_sum OR w.gems <> l.gems_sum;" 2>/dev/null || echo 'ERR')"
if [[ "$mismatch" == "0" ]]; then
  echo "        [OK] 0 筆不一致"
else
  echo "❌ 對帳不一致=${mismatch}（或查詢失敗）→ 立即宣告 P0，RUNBOOK §7.8"
  exit 1
fi

echo "────────────────────────────────────────────────────────────────"
echo "✅ deploy-rollback 完成並通過驗證（RUNBOOK §9.4）；請觸發 On-Call Handoff（§14）"
