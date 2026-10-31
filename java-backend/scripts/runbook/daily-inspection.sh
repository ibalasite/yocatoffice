#!/usr/bin/env bash
# scripts/runbook/daily-inspection.sh
# ─────────────────────────────────────────────────────────────────────────────
# RUNBOOK §8 例行維護（日常巡檢）+ §7.9 磁碟壓力 + §8.4 容量審查 + metric snapshot。
#
# 唯讀巡檢（每項獨立，不中斷；最後印總結）。對應 RUNBOOK §日常巡檢：
#   1. Pod 狀態（API ≥ MIN_REPLICAS Running；§8.4 Iron Constraint replicas≥2）
#   2. 副本/容量快照（replicas、kubectl top；§8.4）
#   3. 磁碟使用（DB data 目錄 df -h；§7.9，使用率 < 85%）
#   4. Metric snapshot（錢包對帳 0 不一致 §7.8 + Outbox pending < 100 §7.4，查真實資料表）
#   5. DB 索引健康（排行榜 idx_scan 增長；§8.2）
#   6. 近期錯誤 log 計數（§8.3；log 由 containerd 管理、無需手動輪換）
#
# 注意：§8.3 log 輪換為 K8s/containerd 自動管理（pod stdout），本腳本僅「巡檢」log 錯誤量，
# 不做手動 log rotate（RUNBOOK 明示無需手動輪換 → 不加 RUNBOOK 未定義的步驟）。
#
# 退出碼：0 全部健康；1 有一項以上異常（細節見 [WARN]/[FAIL]）。
set -euo pipefail

KUBECTL="${KUBECTL:-kubectl}"
APP_NS="${APP_NS:-yocat-office-backend-local}"
DB_NS="${DB_NS:-yocat}"
API_DEPLOY="${API_DEPLOY:-yocat-office-backend-api}"
DB_DEPLOY="${DB_DEPLOY:-yocat-pg}"
DB_USER="${DB_USER:-yocat}"
DB_NAME="${DB_NAME:-yocat}"
MIN_REPLICAS="${MIN_REPLICAS:-2}"
DISK_WARN_PCT="${DISK_WARN_PCT:-85}"            # §7.9 磁碟壓力閾值
OUTBOX_WARN="${OUTBOX_WARN:-100}"               # §7.4 Outbox 積壓閾值
PGDATA_DIR="${PGDATA_DIR:-/var/lib/postgresql/data}"

RC=0
ok()   { echo "  [OK]   $*"; }
warn() { echo "  [WARN] $*"; RC=1; }
fail() { echo "  [FAIL] $*"; RC=1; }

psql_q() { "$KUBECTL" exec -n "$DB_NS" "deploy/${DB_DEPLOY}" -- psql -U "$DB_USER" -d "$DB_NAME" -tAc "$1" 2>/dev/null || echo 'ERR'; }

echo "════════════════════════════════════════════════════════════════"
echo " daily-inspection  ns=$APP_NS  ($(date '+%F %T'))"
echo "════════════════════════════════════════════════════════════════"

# ── 1. Pod 狀態 ────────────────────────────────────────────────────────────
echo "[1/6] API pods"
running="$("$KUBECTL" get pods -n "$APP_NS" -l "app=${API_DEPLOY}" --no-headers 2>/dev/null | grep -c 'Running' || true)"
[[ "$running" -ge "$MIN_REPLICAS" ]] && ok "${running} Running (≥ ${MIN_REPLICAS})" \
  || fail "${running} Running (< ${MIN_REPLICAS}) → RUNBOOK §7.6"

# ── 2. 副本/容量快照（§8.4）────────────────────────────────────────────────
echo "[2/6] 容量快照 (§8.4; 下限恆為 ${MIN_REPLICAS})"
"$KUBECTL" get hpa "$API_DEPLOY" -n "$APP_NS" --no-headers 2>/dev/null | sed 's/^/        hpa: /' \
  || echo "        (無 HPA；本機以固定 replicas=${MIN_REPLICAS} 維持 HA)"
"$KUBECTL" top pods -n "$APP_NS" -l "app=${API_DEPLOY}" --no-headers 2>/dev/null | sed 's/^/        top: /' \
  || echo "        (metrics-server 未安裝，略過 top)"
ok "容量快照已輸出（人工判讀 CPU<70% / mem<75%）"

# ── 3. 磁碟使用（§7.9）──────────────────────────────────────────────────────
echo "[3/6] DB 磁碟使用 (§7.9; 閾值 ${DISK_WARN_PCT}%)"
usep="$("$KUBECTL" exec -n "$DB_NS" "deploy/${DB_DEPLOY}" -- df -P "$PGDATA_DIR" 2>/dev/null | awk 'NR==2{gsub("%","",$5); print $5}' || echo 'ERR')"
if [[ "$usep" =~ ^[0-9]+$ ]]; then
  [[ "$usep" -lt "$DISK_WARN_PCT" ]] && ok "使用率 ${usep}%" \
    || warn "使用率 ${usep}% ≥ ${DISK_WARN_PCT}% → 查 WAL 堆積/擴容 PVC（§7.9 / §7.5.2）"
else
  warn "df 讀取失敗（路徑 ${PGDATA_DIR} 可能不同）"
fi

# ── 4. Metric snapshot：對帳 + Outbox（查真實資料表）────────────────────────
echo "[4/6] 錢包對帳 + Outbox 積壓"
mismatch="$(psql_q "SELECT count(*) FROM wallet.wallets w JOIN (
     SELECT account_id,
            SUM(CASE WHEN currency='cash' THEN delta ELSE 0 END) cash_sum,
            SUM(CASE WHEN currency='gems' THEN delta ELSE 0 END) gems_sum
     FROM wallet.wallet_ledger GROUP BY account_id) l ON l.account_id=w.account_id
   WHERE w.cash <> l.cash_sum OR w.gems <> l.gems_sum;")"
[[ "$mismatch" == "0" ]] && ok "對帳 0 筆不一致" \
  || fail "對帳不一致=${mismatch} → P0，RUNBOOK §7.8"
# Outbox = modulith.event_publication where completion_date IS NULL（SCHEMA §3.8.1 / EDD §4.6）
pending="$(psql_q "SELECT count(*) FROM modulith.event_publication WHERE completion_date IS NULL;")"
if [[ "$pending" =~ ^[0-9]+$ ]]; then
  [[ "$pending" -lt "$OUTBOX_WARN" ]] && ok "Outbox pending=${pending} (< ${OUTBOX_WARN})" \
    || warn "Outbox pending=${pending} ≥ ${OUTBOX_WARN} → RUNBOOK §7.4"
else
  warn "event_publication 查詢失敗（§7.4）"
fi

# ── 5. DB 索引健康（§8.2）───────────────────────────────────────────────────
echo "[5/6] 排行榜索引使用 (§8.2)"
idxrows="$(psql_q "SELECT indexrelname||'='||idx_scan FROM pg_stat_user_indexes WHERE schemaname='leaderboard';")"
if [[ "$idxrows" != "ERR" && -n "$idxrows" ]]; then
  echo "$idxrows" | sed 's/^/        /'
  ok "leaderboard 索引統計已輸出（idx_scan 應持續增長）"
else
  warn "無 leaderboard 索引統計（schema 未建或無流量）"
fi

# ── 6. 近期錯誤 log 計數（§8.3）─────────────────────────────────────────────
echo "[6/6] 近期錯誤 log (§8.3; containerd 自動管理，不手動輪換)"
errc="$("$KUBECTL" logs -n "$APP_NS" -l "app=${API_DEPLOY}" --tail=500 2>/dev/null | grep -cE 'ERROR|Exception' || true)"
[[ "$errc" -eq 0 ]] && ok "近 500 行無 ERROR/Exception" \
  || warn "近 500 行有 ${errc} 筆 ERROR/Exception → 讀 log 辨識來源（§7.1）"

echo "────────────────────────────────────────────────────────────────"
if [[ "$RC" -eq 0 ]]; then echo "✅ daily-inspection：全部健康"; else echo "⚠ daily-inspection：有項目需追蹤（見 [WARN]/[FAIL]）"; fi
exit "$RC"
