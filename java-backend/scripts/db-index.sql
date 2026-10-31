-- ════════════════════════════════════════════════════════════════════════════
-- scripts/db-index.sql — O-13 DDL Index & Constraint Script
--   yocat-office-backend（PostgreSQL 16 / Spring Modulith HC-1，7 BC + modulith/platform）
--
-- 目的：對已套用 Flyway migration（migrations/V001–V012）的資料庫，做「索引與約束」
--       的冪等對齊與驗證層（SCHEMA.md §5 索引策略 / §7 效能設計 / §9 資料完整性約束）。
--
-- 與 foundation-migrations 的協調（不重複建表、只補/驗索引與約束）：
--   - 本腳本「不含」任何 CREATE TABLE / CREATE TYPE / CREATE FUNCTION / CREATE TRIGGER。
--     表、ENUM、trigger function 的建立權威唯一歸屬 migrations/（Flyway source of truth）。
--   - 全部索引語句一律以 `IF NOT EXISTS` 冪等形式重述 SCHEMA §5 定義的同一組索引。
--     因此本腳本可在 migration 已建好同名索引後安全重跑：同名索引存在即略過，不會
--     拋「already exists」錯誤，也不會重複建立同一索引（Anti-Fake 紅線：無重複建立）。
--   - 用途場景：新建 read replica 索引對齊、索引重建後補齊、CI/部署後的索引存在性驗證。
--
-- 執行前置：migrations/V001–V010（schema + 全表）必須已套用（本腳本只建索引、不建表）。
-- 執行方式：psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f scripts/db-index.sql
--
-- 命名規範（SCHEMA §2.1）：一般索引 idx_{table}_{columns}；唯一索引 uq_{table}_{columns}。
-- 索引型別選用（SCHEMA §5.2）：B-tree（等值/範圍/排序，預設）、BRIN（時間有序高寫入大表）、
--   Partial（軟刪除 WHERE deleted_at IS NULL / Outbox 待投遞 / 對帳不一致）。
-- 複合索引欄位順序（SCHEMA §5.3）：等值條件欄位在前、範圍/排序欄位在後。
--
-- Covering（覆蓋查詢）策略說明（SCHEMA §5.1 / §7.1，對應 template Review #3）：
--   SCHEMA 以「複合索引 + 刻意反正規化」達成高頻 SELECT 的覆蓋需求，不使用 INCLUDE 冗餘欄位：
--   - idx_ledger_acct(account_id, created_at DESC)：覆蓋 GET /wallet/ledger 依帳號查近期（§7.1 P95<20ms）。
--   - idx_lb_mode_score(mode, score DESC)：覆蓋排行 Top N 掃描（§7.1 P99<300ms）；
--       SELECT 的 display_name 由 leaderboard.display_name 冗餘快照承載（§4.2 Conformist），
--       免跨 BC JOIN，故無需 INCLUDE member 欄位（HC-1 合規）。
--   - idx_records_acct(account_id, type, created_at DESC)：覆蓋 GET /records 依帳號/類型分頁（§7.5【1】keyset）。
--   SCHEMA 未指定任何 INCLUDE/額外覆蓋索引，故本腳本不新增（避免 gold-plating，template Review #9）。
-- ════════════════════════════════════════════════════════════════════════════

-- ──────────────────────────────────────────────────────────────────────────────
-- §1  BC schema 索引（SCHEMA §5.1 清單 + §3 各表定義，與 migrations V002–V008 對齊）
-- ──────────────────────────────────────────────────────────────────────────────

-- ── member BC（V002，SCHEMA §3.1）──────────────────────────────────────────────
-- email 大小寫不敏感唯一（僅未刪除帳號）；對應 POST /auth/login、/register（§7.1 P95<10ms）
CREATE UNIQUE INDEX IF NOT EXISTS uq_accounts_email
  ON member.accounts (LOWER(email)) WHERE deleted_at IS NULL;
-- 依帳號列舉/清理 session（EDD §6.2）
CREATE INDEX IF NOT EXISTS idx_sessions_acct
  ON member.sessions (account_id);
-- 定時清理過期 session（expires_at < now()）
CREATE INDEX IF NOT EXISTS idx_sessions_expires
  ON member.sessions (expires_at);
-- 限流滑動視窗後援：依來源 IP + 時間（CONSTANTS §6）
CREATE INDEX IF NOT EXISTS idx_login_attempts_ip
  ON member.login_attempts (ip_address, created_at DESC);
-- 依 email 查近期嘗試（僅 email 非空），防暴力破解
CREATE INDEX IF NOT EXISTS idx_login_attempts_mail
  ON member.login_attempts (LOWER(email), created_at DESC) WHERE email IS NOT NULL;
-- 帳號刪除流程：掃描未完成請求（Partial，排除 completed）
CREATE INDEX IF NOT EXISTS idx_del_req_status
  ON member.account_deletion_requests (status, requested_at) WHERE status <> 'completed';
-- 密碼歷史依帳號查近期（防重用，EDD §9.1）
CREATE INDEX IF NOT EXISTS idx_pwd_hist_acct
  ON member.password_history (account_id, created_at DESC);

-- ── wallet BC（V003，SCHEMA §3.2）──────────────────────────────────────────────
-- 帳本對帳/查近期：account_id（等值）在前、created_at DESC（排序）在後（EDD §6.2，§7.1 P95<20ms）
CREATE INDEX IF NOT EXISTS idx_ledger_acct
  ON wallet.wallet_ledger (account_id, created_at DESC);
-- 對帳不一致掃描（Partial，WHERE is_consistent = FALSE → P0 告警，CONSTANTS §4）
CREATE INDEX IF NOT EXISTS idx_wallet_snap_incons
  ON wallet.wallet_balance_snapshots (snapshot_at DESC) WHERE is_consistent = FALSE;
-- 入站事件依 topic 查近期（exactly-once 稽核，EDD §4.6）
CREATE INDEX IF NOT EXISTS idx_wallet_inbound_topic
  ON wallet.wallet_inbound_events (topic_name, processed_at DESC);

-- ── deposit BC（V004，SCHEMA §3.3）─────────────────────────────────────────────
-- 冪等去重（Idempotency-Key，UNIQUE；API §7）
CREATE UNIQUE INDEX IF NOT EXISTS uq_deposit_order_idem
  ON deposit.deposit_order (idempotency_key);
-- 依帳號查訂單史
CREATE INDEX IF NOT EXISTS idx_deposit_order_acct
  ON deposit.deposit_order (account_id, created_at DESC);
-- 冪等鍵過期清理（24h，API §7.4）
CREATE INDEX IF NOT EXISTS idx_deposit_idem_expires
  ON deposit.deposit_idempotency (expires_at);
-- 金流 webhook 事件去重（provider_event_id，UNIQUE；API §8）
CREATE UNIQUE INDEX IF NOT EXISTS uq_webhook_provider_event
  ON deposit.deposit_payment_webhook (provider_event_id);

-- ── entitlement BC（V005，SCHEMA §3.4）─────────────────────────────────────────
-- 目錄依種類篩上架項目（Partial，WHERE is_active）
CREATE INDEX IF NOT EXISTS idx_entitlement_catalog_kind
  ON entitlement.entitlement_catalog (kind) WHERE is_active;
-- 防重複授予 + 查擁有（Partial Unique，排除軟刪除；破產不刪，PRD §5.3）
CREATE UNIQUE INDEX IF NOT EXISTS uq_entitlement_acct_kind_item
  ON entitlement.entitlements (account_id, kind, item_id) WHERE deleted_at IS NULL;
-- GET /entitlements 查擁有（Partial，排除軟刪除）
CREATE INDEX IF NOT EXISTS idx_entitlement_acct
  ON entitlement.entitlements (account_id) WHERE deleted_at IS NULL;
-- 授予審計依帳號查近期（EntitlementGranted 事件來源）
CREATE INDEX IF NOT EXISTS idx_grant_log_acct
  ON entitlement.entitlement_grant_log (account_id, granted_at DESC);

-- ── record BC（V006，SCHEMA §3.5）──────────────────────────────────────────────
-- GET /records 依帳號/類型分頁（account_id 等值 + type + created_at DESC 排序，EDD §6.2）
CREATE INDEX IF NOT EXISTS idx_records_acct
  ON record.records (account_id, type, created_at DESC);

-- ── leaderboard BC（V007，SCHEMA §3.6）─────────────────────────────────────────
-- 排行 Top N by mode（mode 等值 + score DESC 排序，P99<300ms，EDD §6.2）
CREATE INDEX IF NOT EXISTS idx_lb_mode_score
  ON leaderboard.leaderboard (mode, score DESC);
-- Top N 快照依模式查近期（Redis 降級備援，EDD §11.3）
CREATE INDEX IF NOT EXISTS idx_lb_snapshot_mode
  ON leaderboard.leaderboard_snapshot (mode, captured_at DESC);

-- ── game_save BC（V008，SCHEMA §3.7）───────────────────────────────────────────
-- 存檔寫入審計依帳號查近期（PRD §5.4）
CREATE INDEX IF NOT EXISTS idx_save_write_acct
  ON game_save.save_write_log (account_id, written_at DESC);

-- ──────────────────────────────────────────────────────────────────────────────
-- §2  橫切 schema 索引（modulith / platform，與 migrations V009–V010 對齊）
-- ──────────────────────────────────────────────────────────────────────────────

-- ── modulith（V009，Spring Modulith Outbox）────────────────────────────────────
-- Outbox 待投遞掃描（Partial，WHERE completion_date IS NULL；Transactional Outbox 轉發）
CREATE INDEX IF NOT EXISTS idx_event_pub_incomplete
  ON modulith.event_publication (publication_date) WHERE completion_date IS NULL;
-- 已完成事件歸檔依完成時間查
CREATE INDEX IF NOT EXISTS idx_event_archive_date
  ON modulith.event_publication_archive (completion_date);
-- DLQ 依 topic 查近期（重試 5 次後，EDD §4.6.1）
CREATE INDEX IF NOT EXISTS idx_dlq_topic
  ON modulith.event_dead_letter (topic_name, dead_lettered_at DESC);

-- ── platform（V010，稽核/合規/設定/觀測/限流後援/HA）──────────────────────────
-- 稽核日誌依表+記錄查（EDD §10.6）
CREATE INDEX IF NOT EXISTS idx_audit_table_record
  ON platform.audit_log (table_name, record_id);
-- 稽核日誌依操作者查（Partial，WHERE actor_account_id IS NOT NULL）
CREATE INDEX IF NOT EXISTS idx_audit_actor
  ON platform.audit_log (actor_account_id) WHERE actor_account_id IS NOT NULL;
-- 大型稽核表時間範圍查詢（BRIN，物理時間有序高寫入，SCHEMA §5.2）
CREATE INDEX IF NOT EXISTS idx_audit_created_brin
  ON platform.audit_log USING BRIN (created_at);
-- 資料存取日誌依表+記錄查（§17.4 GDPR）
CREATE INDEX IF NOT EXISTS idx_dal_record
  ON platform.data_access_logs (table_name, record_id);
-- 資料存取日誌依存取時間查
CREATE INDEX IF NOT EXISTS idx_dal_accessed
  ON platform.data_access_logs (accessed_at);
-- GDPR 抹除請求掃描未完成（Partial，WHERE status <> 'completed'，§17.2）
CREATE INDEX IF NOT EXISTS idx_gdpr_status
  ON platform.gdpr_erasure_requests (status, requested_at) WHERE status <> 'completed';
-- Feature flag 稽核依 flag 查近期（EDD §20.6）
CREATE INDEX IF NOT EXISTS idx_flag_audit_key
  ON platform.feature_flag_audit (flag_key, changed_at DESC);
-- 限流計數後援唯一鍵（action, identifier, window_start；UNIQUE，CONSTANTS §6 / SCHEMA §9.3）
CREATE UNIQUE INDEX IF NOT EXISTS uq_rate_action_id_window
  ON platform.rate_limit_counters (action, identifier, window_start);
-- API 請求日誌依 trace 關聯（EDD §10.1 可觀測性）
CREATE INDEX IF NOT EXISTS idx_api_log_trace
  ON platform.api_request_log (trace_id);
-- 高寫入日誌時間範圍（BRIN，SCHEMA §5.2 / §10.1 分區前置）
CREATE INDEX IF NOT EXISTS idx_api_log_created_brin
  ON platform.api_request_log USING BRIN (created_at);
-- SLO 量測依指標查近期（EDD §10.5，CONSTANTS §4）
CREATE INDEX IF NOT EXISTS idx_slo_metric
  ON platform.slo_measurements (metric_name, measured_at DESC);
-- 健康檢查日誌依檢查名查近期（EDD §10.7）
CREATE INDEX IF NOT EXISTS idx_health_check
  ON platform.health_check_log (check_name, checked_at DESC);
-- 複寫狀態日誌依 replica 查近期（Replica Lag 監控 ≤1s，§18 / EDD §3.6）
CREATE INDEX IF NOT EXISTS idx_repl_status
  ON platform.replication_status_log (replica_name, checked_at DESC);

-- ──────────────────────────────────────────────────────────────────────────────
-- §3  索引對齊驗證（確認 SCHEMA §5 全 38 索引實際存在於資料庫）
--     任一缺失 → RAISE EXCEPTION（搭配 psql -v ON_ERROR_STOP=1 讓 CI 失敗）
-- ──────────────────────────────────────────────────────────────────────────────
DO $$
DECLARE
  expected_idx TEXT[] := ARRAY[
    -- member
    'uq_accounts_email','idx_sessions_acct','idx_sessions_expires',
    'idx_login_attempts_ip','idx_login_attempts_mail','idx_del_req_status','idx_pwd_hist_acct',
    -- wallet
    'idx_ledger_acct','idx_wallet_snap_incons','idx_wallet_inbound_topic',
    -- deposit
    'uq_deposit_order_idem','idx_deposit_order_acct','idx_deposit_idem_expires','uq_webhook_provider_event',
    -- entitlement
    'idx_entitlement_catalog_kind','uq_entitlement_acct_kind_item','idx_entitlement_acct','idx_grant_log_acct',
    -- record
    'idx_records_acct',
    -- leaderboard
    'idx_lb_mode_score','idx_lb_snapshot_mode',
    -- game_save
    'idx_save_write_acct',
    -- modulith
    'idx_event_pub_incomplete','idx_event_archive_date','idx_dlq_topic',
    -- platform
    'idx_audit_table_record','idx_audit_actor','idx_audit_created_brin',
    'idx_dal_record','idx_dal_accessed','idx_gdpr_status','idx_flag_audit_key',
    'uq_rate_action_id_window','idx_api_log_trace','idx_api_log_created_brin',
    'idx_slo_metric','idx_health_check','idx_repl_status'
  ];
  missing TEXT[] := '{}';
  ix TEXT;
BEGIN
  FOREACH ix IN ARRAY expected_idx LOOP
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = ix) THEN
      missing := array_append(missing, ix);
    END IF;
  END LOOP;
  IF array_length(missing, 1) IS NOT NULL THEN
    RAISE EXCEPTION 'SCHEMA §5 index alignment FAILED — missing %/% indexes: %',
      array_length(missing, 1), array_length(expected_idx, 1), array_to_string(missing, ', ');
  END IF;
  RAISE NOTICE 'SCHEMA §5 index alignment OK — all % indexes present', array_length(expected_idx, 1);
END $$;

-- ──────────────────────────────────────────────────────────────────────────────
-- §4  約束對齊驗證（SCHEMA §9.1 同 BC FK / §9.2 CHECK / §9.3 UNIQUE）
--     約束本體由 migrations 內嵌於 CREATE TABLE 建立；本段只驗證其存在性，不重建。
--     §9.3 的 5 個 UNIQUE 皆以唯一索引實作，已於 §3 索引清單驗證過。
--     §9.4：本系統無循環 FK（跨 BC 皆 app-layer ID），不需 DEFERRABLE / 進階約束。
-- ──────────────────────────────────────────────────────────────────────────────
DO $$
DECLARE
  n_fk    INT;
  n_check INT;
  n_uniq  INT;
BEGIN
  -- §9.1 同 BC FK（HC-1 允許同 schema 內 FK；跨 BC 一律無 FK）
  --   account_deletion_requests→accounts、password_history→accounts、deposit_order→deposit_pack、
  --   deposit_payment_webhook→deposit_order、entitlements→entitlement_catalog、
  --   records→record_type_catalog、feature_flag_audit→feature_flags（共 7）
  SELECT count(*) INTO n_fk
  FROM pg_constraint
  WHERE contype = 'f'
    AND connamespace::regnamespace::text IN
        ('member','wallet','deposit','entitlement','record','leaderboard','game_save','platform');
  IF n_fk < 7 THEN
    RAISE EXCEPTION 'SCHEMA §9.1 same-BC FK alignment FAILED — expected >= 7, found %', n_fk;
  END IF;

  -- §9.2 CHECK 約束（wallets.cash/gems、ledger.balance/reason/currency、accounts.display_name、
  --   deposit_pack.amount/bonus_pct、save_write_log.data_bytes、leaderboard_snapshot.top_limit、
  --   rate_limit_counters.action 等業務值域）
  SELECT count(*) INTO n_check
  FROM pg_constraint
  WHERE contype = 'c'
    AND connamespace::regnamespace::text IN
        ('member','wallet','deposit','entitlement','record','leaderboard','game_save','platform');
  IF n_check < 10 THEN
    RAISE EXCEPTION 'SCHEMA §9.2 CHECK alignment FAILED — expected >= 10, found %', n_check;
  END IF;

  -- §9.3 UNIQUE（5 個，以唯一索引實作）
  SELECT count(*) INTO n_uniq
  FROM pg_indexes
  WHERE indexname IN ('uq_accounts_email','uq_entitlement_acct_kind_item',
                      'uq_deposit_order_idem','uq_webhook_provider_event','uq_rate_action_id_window');
  IF n_uniq < 5 THEN
    RAISE EXCEPTION 'SCHEMA §9.3 UNIQUE alignment FAILED — expected 5, found %', n_uniq;
  END IF;

  RAISE NOTICE 'SCHEMA §9 constraint alignment OK — fk=% check=% unique=%', n_fk, n_check, n_uniq;
END $$;

-- ──────────────────────────────────────────────────────────────────────────────
-- §5  索引膨脹監控參考查詢（SCHEMA §5.5，運維用；本段為唯讀查詢，不改結構）
--     部署後由 DBA / 監控 Job 執行，用於偵測未使用索引與重建膨脹索引。
-- ──────────────────────────────────────────────────────────────────────────────
-- 從未被使用的索引（idx_scan = 0）：
--   SELECT schemaname, relname AS table_name, indexrelname AS index_name,
--          pg_size_pretty(pg_relation_size(indexrelid)) AS index_size, idx_scan
--   FROM pg_stat_user_indexes
--   WHERE idx_scan = 0
--   ORDER BY pg_relation_size(indexrelid) DESC;
--
-- 線上重建膨脹索引（不鎖表，範例）：
--   REINDEX INDEX CONCURRENTLY wallet.idx_ledger_acct;
-- ════════════════════════════════════════════════════════════════════════════
-- End of scripts/db-index.sql — SCHEMA §5 全 38 索引 + §9 約束，冪等對齊 migrations V001–V012
-- ════════════════════════════════════════════════════════════════════════════
