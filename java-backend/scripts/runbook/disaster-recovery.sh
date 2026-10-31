#!/usr/bin/env bash
# scripts/runbook/disaster-recovery.sh
# ─────────────────────────────────────────────────────────────────────────────
# RUNBOOK §10 備份與還原（Backup and Restore）+ §11.3 Step 4 session 快取撤銷 + §8.3 log 封存。
#
# 子命令（對應 RUNBOOK §災難復原）：
#   backup       DB 全量備份（pg_dump → $BACKUP_DIR，對應 §10.1 備份策略）
#   restore FILE DB 還原（pg_restore --clean --if-exists，§10.2；預設 safety-prompt，CONFIRM=1 跳過）
#   verify       還原後驗證（pg_isready + health/ready + 錢包對帳 0 不一致，§10.3）
#   redis-flush  清 member:session:* session 快取（§11.3 Step 4；DB 為 session 權威 TTL）
#   log-archive  封存近期 api-server log 到 $BACKUP_DIR（§8.3；pod log 由 containerd 管理）
#
# 真實基準：yocat-pg（namespace yocat，PostgreSQL 16，db/user=yocat，docs/req/postgres.yaml）；
# redis（namespace yocat-office-backend-local）；session key 前綴 member:session:*（EDD §11.3E / RUNBOOK §11.3）。
# 密碼由 Secret 經環境變數注入 pod，命令行不帶明文（RUNBOOK §10.2 安全要求）。
#
# 用法：bash scripts/runbook/disaster-recovery.sh <backup|restore <file>|verify|redis-flush|log-archive>
set -euo pipefail

KUBECTL="${KUBECTL:-kubectl}"
CURL="${CURL:-curl}"
APP_NS="${APP_NS:-yocat-office-backend-local}"
DB_NS="${DB_NS:-yocat}"
API_DEPLOY="${API_DEPLOY:-yocat-office-backend-api}"
DB_DEPLOY="${DB_DEPLOY:-yocat-pg}"
REDIS_DEPLOY="${REDIS_DEPLOY:-redis}"
DB_USER="${DB_USER:-yocat}"
DB_NAME="${DB_NAME:-yocat}"
BASE_URL="${BASE_URL:-http://yocat-office-backend.local}"
BACKUP_DIR="${BACKUP_DIR:-./.runbook-backups}"
CONFIRM="${CONFIRM:-0}"

usage() {
  grep -E '^#( |$)' "$0" | sed 's/^# \{0,1\}//'
  echo ""
  echo "子命令：backup | restore <dump-file> | verify | redis-flush | log-archive"
}

ts() { date '+%Y%m%d-%H%M%S'; }

do_backup() {
  mkdir -p "$BACKUP_DIR"
  local out="${BACKUP_DIR}/yocat-${DB_NAME}-$(ts).dump"
  echo "[backup] pg_dump ${DB_DEPLOY}/${DB_NAME} → ${out}"
  # custom format（-Fc）供 pg_restore；密碼由 pod 內 Secret 注入，命令行不帶明文
  "$KUBECTL" exec -n "$DB_NS" "deploy/${DB_DEPLOY}" -- pg_dump -U "$DB_USER" -d "$DB_NAME" -Fc > "$out"
  if [[ -s "$out" ]]; then
    echo "✅ backup 完成：${out} ($(wc -c < "$out") bytes)"
  else
    echo "❌ backup 產物為空 → 確認 DB 連線（RUNBOOK §7.5）"; rm -f "$out"; exit 1
  fi
}

do_restore() {
  local file="${1:-}"
  [[ -n "$file" && -s "$file" ]] || { echo "❌ restore 需有效 dump 檔：restore <file>"; exit 1; }
  echo "[restore] 目標 DB=${DB_DEPLOY}/${DB_NAME}  來源=${file}"
  echo "          pg_restore --clean --if-exists（RUNBOOK §10.2：建議還原到隔離目標，勿覆寫現役 Primary）"
  if [[ "$CONFIRM" != "1" ]]; then
    echo "❌ 破壞性操作：請設 CONFIRM=1 確認（確認已指向隔離還原目標而非現役 Primary）後重跑。"
    exit 1
  fi
  "$KUBECTL" exec -i -n "$DB_NS" "deploy/${DB_DEPLOY}" -- pg_restore -U "$DB_USER" -d "$DB_NAME" --clean --if-exists < "$file"
  echo "✅ pg_restore 完成 → 執行 'verify' 子命令確認對帳一致後方可提升為 Primary（§10.3）"
}

do_verify() {
  local rc=0
  echo "[verify] pg_isready"
  "$KUBECTL" exec -n "$DB_NS" "deploy/${DB_DEPLOY}" -- pg_isready -U "$DB_USER" -d "$DB_NAME" >/dev/null 2>&1 \
    && echo "        [OK] accepting connections" || { echo "        [FAIL] pg_isready"; rc=1; }
  echo "[verify] health/ready checks.db"
  body="$("$CURL" -s --max-time 10 "${BASE_URL}/health/ready" || true)"
  echo "$body" | grep -q '"db":"UP"' && echo "        [OK] db:UP" || { echo "        [FAIL] ${body}"; rc=1; }
  echo "[verify] 錢包對帳 0 不一致"
  mismatch="$("$KUBECTL" exec -n "$DB_NS" "deploy/${DB_DEPLOY}" -- psql -U "$DB_USER" -d "$DB_NAME" -tAc \
    "SELECT count(*) FROM wallet.wallets w JOIN (
       SELECT account_id,
              SUM(CASE WHEN currency='cash' THEN delta ELSE 0 END) cash_sum,
              SUM(CASE WHEN currency='gems' THEN delta ELSE 0 END) gems_sum
       FROM wallet.wallet_ledger GROUP BY account_id) l ON l.account_id=w.account_id
     WHERE w.cash <> l.cash_sum OR w.gems <> l.gems_sum;" 2>/dev/null || echo 'ERR')"
  [[ "$mismatch" == "0" ]] && echo "        [OK] 0 筆不一致" || { echo "        [FAIL] mismatch=${mismatch}（§7.8）"; rc=1; }
  [[ "$rc" -eq 0 ]] && echo "✅ 還原後驗證通過（§10.3）" || { echo "❌ 驗證未過：改用前一全量備份點重試（§10.2）"; exit 1; }
}

do_redis_flush() {
  echo "[redis-flush] 清 member:session:* session 快取（§11.3 Step 4；DB 為 session 權威 TTL）"
  if [[ "$CONFIRM" != "1" ]]; then
    echo "❌ 將使所有在線 session 快取失效：設 CONFIRM=1 確認後重跑。"
    exit 1
  fi
  # --scan 不阻塞；DB 仍保有 session 權威，清快取僅強制回源，不致 500（§7.7 降級）
  "$KUBECTL" exec -n "$APP_NS" "deploy/${REDIS_DEPLOY}" -- \
    sh -c "redis-cli --scan --pattern 'member:session:*' | xargs -r redis-cli del" || true
  echo "✅ session 快取已清；接著查 Audit Log 評估影響範圍（RUNBOOK §11.5）"
}

do_log_archive() {
  mkdir -p "$BACKUP_DIR"
  local out="${BACKUP_DIR}/api-logs-$(ts).log"
  echo "[log-archive] 封存 ${API_DEPLOY} 近期 log → ${out}"
  "$KUBECTL" logs -n "$APP_NS" -l "app=${API_DEPLOY}" --tail=2000 --prefix=true > "$out" 2>/dev/null || true
  if [[ -s "$out" ]]; then
    echo "✅ log 已封存：${out}（集中式查詢見 Loki，§8.3）"
  else
    echo "⚠ log 封存為空（pod 可能剛重建；--previous 讀前一次）→ RUNBOOK §5.3 / §7.6"
  fi
}

cmd="${1:-}"; shift || true
case "$cmd" in
  backup)      do_backup ;;
  restore)     do_restore "${1:-}" ;;
  verify)      do_verify ;;
  redis-flush) do_redis_flush ;;
  log-archive) do_log_archive ;;
  ""|-h|--help|help) usage ;;
  *) echo "未知子命令：$cmd"; usage; exit 1 ;;
esac
