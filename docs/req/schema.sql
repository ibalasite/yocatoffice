-- yoCat Office 會員系統 schema（伺服器權威）
-- 設計原則：錢包/權益/記錄由伺服器當權威，client 只讀顯示。

CREATE TABLE IF NOT EXISTS accounts (
  id            BIGSERIAL PRIMARY KEY,
  email         TEXT UNIQUE NOT NULL,
  display_name  TEXT NOT NULL,          -- 公司/老闆名
  pass_hash     TEXT NOT NULL,          -- scrypt(salt$hash)
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS sessions (
  token       TEXT PRIMARY KEY,
  account_id  BIGINT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at  TIMESTAMPTZ NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_sessions_acct ON sessions(account_id);

-- 伺服器權威錢包：資本(遊戲內) + 寶石(付費貨幣)
CREATE TABLE IF NOT EXISTS wallets (
  account_id  BIGINT PRIMARY KEY REFERENCES accounts(id) ON DELETE CASCADE,
  cash        BIGINT NOT NULL DEFAULT 0,
  gems        BIGINT NOT NULL DEFAULT 0,
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 每一筆錢包異動：審計 + 防作弊 + 儲值記錄
CREATE TABLE IF NOT EXISTS wallet_ledger (
  id          BIGSERIAL PRIMARY KEY,
  account_id  BIGINT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
  currency    TEXT NOT NULL,              -- 'cash' | 'gems'
  delta       BIGINT NOT NULL,
  reason      TEXT NOT NULL,              -- 'topup' | 'buy_cosmetic' | 'hire_dream' | 'rescue' | 'init'
  balance     BIGINT NOT NULL,           -- 異動後餘額
  meta        JSONB,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_ledger_acct ON wallet_ledger(account_id, created_at DESC);

-- 已購權益：外觀/夢想員工/劇本解鎖（永不因破產消失）
CREATE TABLE IF NOT EXISTS entitlements (
  id          BIGSERIAL PRIMARY KEY,
  account_id  BIGINT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
  kind        TEXT NOT NULL,              -- 'cosmetic' | 'dream' | 'scenario'
  item_id     TEXT NOT NULL,
  acquired_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE(account_id, kind, item_id)
);

-- 辦公室存檔（跨裝置還原）
CREATE TABLE IF NOT EXISTS saves (
  account_id  BIGINT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
  slot        TEXT NOT NULL DEFAULT 'main',
  data        JSONB NOT NULL,
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY(account_id, slot)
);

-- 經營史：破產記錄 / 成就 / 傳承（掛在帳號上，這就是「失敗記在某帳號身上」）
CREATE TABLE IF NOT EXISTS records (
  id          BIGSERIAL PRIMARY KEY,
  account_id  BIGINT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
  type        TEXT NOT NULL,              -- 'bankruptcy' | 'achievement' | 'legacy'
  payload     JSONB NOT NULL,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_records_acct ON records(account_id, type, created_at DESC);

-- 全域排行榜
CREATE TABLE IF NOT EXISTS leaderboard (
  run_id        TEXT PRIMARY KEY,
  account_id    BIGINT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
  display_name  TEXT NOT NULL,
  mode          TEXT NOT NULL,
  score         BIGINT NOT NULL,
  grade         TEXT,
  detail        JSONB,
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_lb_mode_score ON leaderboard(mode, score DESC);
