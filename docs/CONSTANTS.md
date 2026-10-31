# CONSTANTS — 全域常數 / 列舉 / 錯誤碼 / 設定鍵（單一真相來源）

<!-- SSOT：本文件為 yoCat Office 後端信任層所有「全域常數、列舉值、錯誤碼、設定鍵、有界上下文邊界常數」的唯一真相來源。 -->
<!-- 所有下游文件（EDD / API / SCHEMA / BDD / test-plan / runbook）與原始碼，必須引用本文件之值，不得自行定義未驗證之常數。 -->
<!-- 來源真相：docs/req/schema.sql、docs/req/server-src/accounts.ts、client/index.html（本地算力遊戲常數），對照 docs/PRD.md、docs/BRD.md、docs/IDEA.md。 -->

---

## Document Control

| 欄位 | 內容 |
|------|------|
| **DOC-ID** | CONSTANTS-YOCAT-OFFICE-BACKEND-20261030 |
| **產品名稱** | yoCat Office 後端信任層（yocat-office-backend） |
| **文件版本** | v1.0 |
| **狀態** | DRAFT |
| **作者** | AI Generated（gendoc gen CONSTANTS） |
| **日期** | 2026-10-30 |
| **上游真相來源** | [schema.sql](req/schema.sql)、[accounts.ts](req/server-src/accounts.ts)、[PRD.md](PRD.md)、[BRD.md](BRD.md)、[IDEA.md](IDEA.md)、client/index.html |
| **同步輸出** | [constants.json](constants.json) |

---

## Change Log

| 版本 | 日期 | 作者 | 變更摘要 |
|------|------|------|---------|
| v1.0 | 2026-10-30 | gendoc gen CONSTANTS | 初稿：從 schema.sql / accounts.ts / client 遊戲常數提取全域常數、列舉、錯誤碼、設定鍵與 7 BC 邊界歸屬 |

---

## 使用指引

> ⚠️ **唯一真相來源（SSOT）**：本文件所有常數、列舉與錯誤碼均源自實際系統（DB schema、後端 API、本地遊戲算力），
> 下游文件（EDD / API / SCHEMA / BDD / test-plan / runbook）與各 BC 實作**必須引用本文件之值**，
> **不得自行定義任何未經本文件登記的量化數值、列舉成員或錯誤碼**。
> 若發現原始碼（schema.sql / accounts.ts）與本文件衝突，**以原始碼為準**並回頭更新本文件。

> **有界上下文（Bounded Context）原則：**
> 1. **邊界常數不跨界**：各 BC 獨佔之列舉（如 wallet 的 `reason`、entitlement 的 `kind`、record 的 `type`）僅於該 BC 內定義與驗證，其他 BC 不得直接引用其內部成員。
> 2. **本地算力常數不上雲**：遊戲模擬常數（`BANKRUPT_CASH`、`MAX_WORKERS` 等）屬**客戶端本地算力 BC（算力留本地）**，後端信任層僅透過 `wallet_ledger.reason`（如 `rescue`）感知其結果，**不持有、不驗證遊戲數值本身**。
> 3. **跨 BC 僅走契約**：常數若需跨界使用，必須經由 Public API 或 Domain Event 傳遞，不得共享可變常數。

---

## §1 遊戲/產品核心數值（Game/Product Core Values）

> 下列常數分兩類：**信任層核心常數**（後端權威）與**本地算力遊戲常數**（客戶端模擬，算力留本地）。歸屬 BC 以「邊界」欄標示。

| 常數名稱 | 數值 | 單位 | 說明 | 邊界（BC） | 來源 |
|---------|------|------|------|-----------|------|
| `SESSION_TTL_DAYS` | 30 | 天 | session token 有效期（可由環境變數覆寫） | member | accounts.ts L7 / PRD §5.1 AC-4、§7.4 |
| `SCRYPT_KEYLEN` | 64 | bytes | scrypt 密碼雜湊輸出長度 | member | accounts.ts L43/L49 |
| `SCRYPT_SALT_BYTES` | 16 | bytes | scrypt 隨機 salt 長度 | member | accounts.ts L42 |
| `SESSION_TOKEN_BYTES` | 32 | bytes | session token 隨機位元組長度（hex 編碼） | member | accounts.ts L68 |
| `WALLET_INIT_CASH` | 0 | cash | 註冊時錢包初始資本 | wallet | accounts.ts L118 / schema.sql L23 |
| `WALLET_INIT_GEMS` | 0 | gems | 註冊時錢包初始寶石 | wallet | accounts.ts L118 / schema.sql L24 |
| `GAME_INIT_CASH` | 20000 | cash | 新公司遊戲內起始現金 | 本地算力（client） | client/index.html L401 |
| `GAME_INIT_MORALE` | 70 | 分 | 公司起始士氣 | 本地算力（client） | client/index.html L401 |
| `GAME_DAY_START_MIN` | 540（9:00） | 分鐘 | 每日上班起始分鐘數（9×60） | 本地算力（client） | client/index.html L401 |
| `GAME_DAY_SETTLE_MIN` | 1080（18:00） | 時刻 | 日終結算時刻（營收−薪資=淨利） | 本地算力（client） | 企画.md §P0、PRD §2 |
| `MAX_WORKERS` | 8 | 人 | 單公司員工上限 | 本地算力（client） | client/index.html L336 |
| `MIN_WORKERS` | 1 | 人 | 單公司員工下限 | 本地算力（client） | client/index.html L336 |
| `INITIAL_ROSTER` | 3 男 3 女（6） | 人 | 初始均衡陣容 | 本地算力（client） | 企画.md §外型 |

---

## §2 倍率與賠率（Multipliers & Payouts）

> 本產品為伺服器信任層，**非博弈/拉霸類遊戲**，無賠率（odds）概念。此處登記的「倍率」為儲值包加量比例（行銷加贈），屬 deposit BC 權威定義。

| 常數名稱 | 數值 | 說明 | 邊界（BC） | 來源 |
|---------|------|------|-----------|------|
| `GEM_PACK_MID_BONUS` | +10% | 中寶石包加量（g_mid：330 vs 基準 300） | deposit | accounts.ts L12 |
| `GEM_PACK_BIG_BONUS` | +16% | 大寶石包加量（g_big：700 vs 基準 600） | deposit | accounts.ts L13 |
| `RESCUE_CASH_FLOOR_MULT` | max(cash+20000, 8000) | 破產注資後保證正現金跑道（取較大者） | 本地算力（client） | client/index.html L1942 |

> 遊戲生產力公式 `生產力 = 技能 × 心情 × 精力 × 部門權重`（企画 §P0）屬本地算力 BC，係數不由後端信任層持有，故不在本信任層常數表中固定數值。

---

## §3 閾值與觸發條件（Thresholds & Triggers）

| 常數名稱 | 數值 | 單位 | 觸發條件與結果 | 邊界（BC） | 來源 |
|---------|------|------|--------------|-----------|------|
| `DISPLAY_NAME_MIN` | 1 | 字 | 公司/老闆名 < 1 字 → 回 400「公司/老闆名需 1–24 字」，不寫入 | member | accounts.ts L109 / PRD §5.1 AC-3 |
| `DISPLAY_NAME_MAX` | 24 | 字 | 公司/老闆名 > 24 字 → 回 400，不寫入 | member | accounts.ts L109 |
| `PASSWORD_MIN` | 6 | 碼 | 密碼 < 6 碼 → 回 400「密碼至少 6 碼」，不寫入 | member | accounts.ts L110 / PRD §5.1 AC-3 |
| `TOPUP_AMOUNT_MIN` | 1 | cash/gems | 儲值 amount ≤ 0 → 回 400「金額不正確」 | wallet | accounts.ts L175 / PRD §5.2 AC-4 |
| `TOPUP_AMOUNT_MAX` | 10,000,000 | cash/gems | 儲值 amount > 1e7 → 回 400「金額不正確」 | wallet | accounts.ts L175 / PRD §5.2 邊界 |
| `SPEND_INSUFFICIENT` | 餘額 < amount | — | 花費/購買餘額不足 → 回 402「餘額不足」+ 回傳現有 wallet，交易回滾 | wallet / entitlement | accounts.ts L214/L249 / PRD §5.2 AC-3 |
| `REQUEST_BODY_MAX` | 4,000,000（4e6） | bytes | 請求 body > 4MB → 中斷連線（req.destroy） | 平台底座 | accounts.ts L34 / PRD §5.4 邊界 |
| `SPEND_REASON_MAXLEN` | 40 | 字元 | 花費 reason 超過 40 字元 → 截斷 | wallet | accounts.ts L195 |
| `LEADERBOARD_LIMIT_DEFAULT` | 20 | 筆 | Top N 查詢未帶 limit → 預設 20 | leaderboard | accounts.ts L145 |
| `LEADERBOARD_LIMIT_MAX` | 100 | 筆 | Top N limit 夾擠於 [1, 100] | leaderboard | accounts.ts L314 |
| `BANKRUPT_CASH` | -15000 | cash | 遊戲內現金 < -15000 → 一律跳破產抉擇窗（含代管） | 本地算力（client） | client/index.html L336/L1929 |
| `BANKRUPT_WARN_CASH` | < 0 | cash | 現金 < 0 → 底部紅色脈動「資金告急」警示條 | 本地算力（client） | client/index.html L1664 |
| `RESCUE_GEMS_COST` | 20 | gems（資本） | 破產時選「繼續投資」→ 扣 20 資本，寫 `reason='rescue'` ledger，現金保證 ≥ 8000 跑道 | 本地算力 → wallet | client/index.html L1933/L1941 |
| `INJECT_CASH` | 20000 | cash | 破產注資補充現金額（保證跑道下限 8000） | 本地算力（client） | client/index.html L1933 |

> 觸發-結果對照（供下游 BDD Given-When-Then 引用）：
> - **破產觸發**：`W.cash < BANKRUPT_CASH` → 跳抉擇窗 → ①注資（扣 `RESCUE_GEMS_COST` 資本、寫 `rescue` ledger、現金 ≥ 8000）／②讓它倒（寫 `records` type=`bankruptcy`、進 recycle）。
> - **recycle 原則**：僅重置當前公司現金/營運，**付費特聘靈魂、校友庫、成就、排行、外觀、記錄一律不奪走**（不作惡底線，PRD §5.3 AC-5）。

---

## §4 SLO / SLI 目標（Service Level Objectives）

| 指標名稱 | 目標值 | 測量方式 | 來源 |
|---------|--------|---------|------|
| 可用性（Availability，Production） | 99.9%（< 8.7h/year） | 健康檢查 + APM；Day1 HA ≥2 replicas、HPA、PDB | PRD §7.2 / BRD §7 |
| 回應時間 P50 | < 200ms @ 500 PCU | APM（Micrometer + Prometheus） | PRD §7.1 |
| 回應時間 P99 | < 1000ms @ 500 PCU（成長期 3,000 PCU < 1500ms） | APM | PRD §7.1 |
| 錯誤率（5xx） | < 1% | HTTP 5xx 比率（告警 > 1% 持續 5 分鐘 → P1） | PRD §7.7.4 / §9.2 |
| HA 滾動更新錯誤率 | < 0.1% | 混沌測試 + APM（殺 pod / rolling update 不中斷） | PRD §5.5 AC-1/AC-2 / §9.1 |
| 排行榜 Top N 查詢 P99 | < 300ms（limit ≤ 100） | APM + `idx_lb_mode_score` | PRD §7.1 |
| 寫入交易吞吐量 | ≥ 200 txn/sec（wallet/entitlement 交易） | 壓測（k6 / Gatling） | PRD §7.1 |
| 錢包對帳一致性 | 0 筆不一致（ledger 100% 相符） | 併發壓測 + 對帳稽核（任一不一致即 P0） | PRD §9.1 / §9.2 |
| RTO / RPO（Production） | RTO 30 min / RPO 5 min | BCP 演練 | PRD §7.2 |

---

## §5 RTP / 機率設計（Return-to-Player / Probability Design）

**N/A — 本產品為伺服器信任層（身分/錢包/權益/記錄/排行/存檔），非博弈或拉霸類遊戲，無 RTP、無隨機賠率、無機率抽獎設計。**

> 說明：yoCat Office 的遊戲性為辦公室經營模擬（確定性模擬），所有隨機性（角色性別/姓名/座位洗牌等）皆屬客戶端本地算力，且不涉及付費期望值回報；信任層只負責權威記錄金流與權益，無任何 RTP 承諾。若未來新增轉蛋/抽獎類付費玩法，須新增獨立 BC 並於本章登記各情境 RTP。

---

## §6 Rate Limit 設定（Rate Limiting）

> 現行原型（accounts.ts）**尚未實作端點層 rate limit**；PRD §7.4 要求安全層具備限流能力。下列為下游 EDD §9 安全設計須落地之建議值（行業通用基準，標注為建議值，待 Engineering 於 EDD 定稿）。

| 端點 / 功能 | 建議限制值 | 時間窗口 | 說明 | 邊界（BC） | 來源 |
|-----------|-----------|---------|------|-----------|------|
| `POST /auth/login` | 10 次 | 每分鐘 / 每 IP | 防暴力破解（密碼比對已用 timingSafeEqual） | member | 建議值（PRD §7.4，EDD 定稿） |
| `POST /auth/register` | 5 次 | 每分鐘 / 每 IP | 防量產帳號 | member | 建議值（EDD 定稿） |
| `POST /wallet/topup`、`/wallet/spend` | 60 次 | 每分鐘 / 每帳號 | 防金流濫用 | wallet | 建議值（EDD 定稿） |
| `POST /entitlements/buy` | 30 次 | 每分鐘 / 每帳號 | 防重複購買衝擊 | entitlement | 建議值（EDD 定稿） |
| `GET /leaderboard` | 120 次 | 每分鐘 / 每 IP | 公開查詢（不需登入），防爬取 | leaderboard | 建議值（EDD 定稿） |

---

## §7 業務規則數值（Business Rule Values）

> 本章登記不屬 §1–§6 的量化業務規則與付費包定義（deposit / wallet BC 權威）。

| 常數名稱 | 數值 | 單位 | 業務規則描述 | 邊界（BC） | 來源 |
|---------|------|------|------------|-----------|------|
| `GEM_PACK.g_small` | 60 | gems | 小寶石包 | deposit | accounts.ts L11 |
| `GEM_PACK.g_mid` | 330 | gems | 中寶石包（+10%） | deposit | accounts.ts L12 |
| `GEM_PACK.g_big` | 700 | gems | 大寶石包（+16%） | deposit | accounts.ts L13 |
| `CAP_PACK.cap_1000` | 1000 | cash | 資本儲值包（假付費，測試用） | deposit | accounts.ts L17 |
| `init` ledger delta | 0 | gems | 註冊時寫入一筆 `reason='init'` 開帳 ledger（balance=0） | wallet | accounts.ts L119 |
| 免費授予 amount | 0 | — | entitlement 購買 amount=0 → 僅插權益、不寫 ledger（活動贈與） | entitlement | accounts.ts L236 / PRD §5.3 邊界 |
| save slot 預設 | `main` | — | 存檔 slot 未指定時預設 main；(account_id, slot) 為主鍵 | game-save | accounts.ts L256/L262 / schema.sql L54 |
| records 查詢上限 | 100 | 筆 | GET /records 單次回傳上限 | record | accounts.ts L282 |

---

## §8 系統容量規劃（Capacity Planning Constants）

| 常數名稱 | 數值 | 單位 | 說明 | 來源 |
|---------|------|------|------|------|
| 最大並發用戶（啟動期） | 500 | PCU | 啟動期設計並發（對應 5,000 DAU） | PRD §7.3 / IDEA §6 Q4 |
| 最大並發用戶（成長期） | 3,000 | PCU | 成長期目標並發（對應 50,000 DAU，10x） | PRD §7.3 / §5.5 邊界 |
| 日活躍用戶（啟動 / 成長） | 5,000 / 50,000 | DAU | 容量規劃基準 | PRD §7.3 / BRD §7 |
| 峰值寫入 TPS | 200 | txn/sec | wallet/entitlement 交易吞吐量下限 | PRD §7.1 |
| HPA 擴容基準 / 上限 | 500 / 3,000 | PCU | CPU/請求量達閾值自動擴容 | PRD §5.5 邊界 |
| 最小副本數 | 2 | replicas | Day1 HA（無 SPOF、PodDisruptionBudget 保最小可用） | PRD §7.2 / §5.5 AC-1 |
| 資料保留（Log，熱 / 冷） | 90 / 365 | 天 | Production 日誌保留（熱 90 天 / 冷 1 年） | PRD §7.7.1 |
| 資料保留（Staging Log） | 14 | 天 | Staging 日誌保留 | PRD §7.7.1 |
| Trace 保留 | 7 | 天 | 分散式追蹤保留 | PRD §7.7.3 |
| `wallet_ledger` 保留 | append-only 永久（Phase B 分區） | — | 審計帳本不刪（帳號刪除時 CASCADE，依法匿名化保留） | schema.sql L29 / PRD §6.3 |

---

## §9 列舉值真相來源（Enumerations — by Bounded Context）

> ⚠️ 下列列舉為**各 BC 獨佔**，成員值以 DB schema 與 accounts.ts 驗證邏輯為準，跨 BC 不得擴充或引用內部成員。

| 列舉 | 允許成員 | 欄位 / 位置 | 邊界（BC） | 驗證點 | 來源 |
|------|---------|-----------|-----------|--------|------|
| **貨幣種類（currency）** | `cash`、`gems` | `wallets.cash/gems`、`wallet_ledger.currency` | wallet | 非 `gems` 一律視為 `cash` | schema.sql L23-24/L32、accounts.ts L171/L193 |
| **錢包異動原因（reason）** | `init`、`topup`、`rescue`、`buy_cosmetic`、`buy_dream`、`buy_scenario` | `wallet_ledger.reason` | wallet | 由各動作寫入（註冊=init、儲值=topup、購買=buy_<kind>、破產注資=rescue） | schema.sql L34、accounts.ts L119/L183/L241 |
| **權益種類（kind）** | `cosmetic`、`dream`、`scenario` | `entitlements.kind` | entitlement | 不在集合內 → 回 400「項目不正確」 | schema.sql L45、accounts.ts L230 |
| **經營史類型（type）** | `bankruptcy`、`achievement`、`legacy` | `records.type` | record | 不在集合內 → 回 400「type 不正確」 | schema.sql L64、accounts.ts L275 |
| **排行模式（mode）** | 自由字串（預設 `sandbox`） | `leaderboard.mode` | leaderboard | 未帶 mode → 預設 `sandbox`；查詢空字串＝全模式 | accounts.ts L291/L144 |
| **存檔槽（slot）** | 自由字串（預設 `main`） | `saves.slot` | game-save | 未帶 slot → 預設 `main` | schema.sql L54、accounts.ts L256 |

> **列舉一致性註記：** schema.sql L34 註解列舉 `reason` 含 `buy_cosmetic`、`hire_dream`，而 accounts.ts L241 的購買動作以 `"buy_" + kind` 動態產生 `buy_cosmetic` / `buy_dream` / `buy_scenario`。`hire_dream`（特聘夢想人才）為語意別名，實作上夢想員工購買寫入為 `buy_dream`。下游實作應以「`buy_<entitlement.kind>` + `topup` + `rescue` + `init`」為 reason 真實集合；`hire_dream` 保留為業務語意同義詞。此差異已標記，供 EDD 統一命名決策。

---

## §10 HTTP 錯誤碼與語意（Error Codes）

> 全域錯誤碼契約（api-only，客戶端須可預期處理）。訊息為 zh-TW，可 i18n key 化。

| HTTP 碼 | 語意 | 觸發情境 | 回應訊息（範例） | 來源 |
|--------|------|---------|----------------|------|
| **200** | 成功 | 正常完成 | 回傳對應資源 | accounts.ts 全域 |
| **204** | 無內容 | CORS 預檢（OPTIONS） | — | accounts.ts L99 |
| **400** | 請求格式/參數錯誤 | email 格式錯、名稱越界、密碼過短、金額不正確、kind/type 不正確、缺 data/runId | 附欄位錯誤訊息 | accounts.ts L108-110/L175/L196/L230/L263/L275/L293 |
| **401** | 未授權 | 無/過期/無效 Bearer token；登入帳密錯誤 | 「請先登入」／「email 或密碼錯誤」 | accounts.ts L134/L157 / PRD §5.1 AC-5/AC-6 |
| **402** | 需付費（餘額不足） | wallet/spend 或 entitlements/buy 餘額 < amount | 「餘額不足」+ 回傳現有 wallet | accounts.ts L214/L249 / PRD §5.2 AC-3 |
| **404** | 找不到 | 命中 API 前綴但無對應路由 | 「api not found」 | accounts.ts L302 |
| **409** | 衝突 | email 已註冊；已擁有同一 (kind,item_id) 權益 | 「這個 email 已經註冊過了」／「已經擁有這個項目」 | accounts.ts L112/L248 / PRD §5.1 AC-2、§5.3 AC-2 |
| **500** | 伺服器錯誤 | 未預期例外（交易回滾，不產生半筆異動） | 錯誤訊息字串 | accounts.ts L305 / PRD §6.2 |

---

## §11 設定鍵（Configuration Keys）

> 環境變數與部署設定鍵，供 EDD / k8s manifest / runbook 引用。

| 設定鍵 | 預設值 | 類型 | 說明 | 邊界 / 位置 | 來源 |
|-------|-------|------|------|-----------|------|
| `SESSION_TTL_DAYS` | 30 | int（天） | session token 有效期 | member / 後端 env | accounts.ts L7 |
| `CONFIG.BACKEND_URL` | `http://localhost:30088`（file:）/ 空（同源） | string | 客戶端呼叫後端位址 | client | client/index.html L243 |
| `CONFIG.USE_BACKEND` | true | bool | 客戶端是否啟用後端信任層 | client | client/index.html L243 |
| CORS `Allow-Origin` | `*` | string | 跨來源允許（Phase A 寬鬆，正式須收斂） | 平台底座 | accounts.ts L22 |
| CORS `Allow-Methods` | `GET,POST,PUT,OPTIONS` | string | 允許的 HTTP 方法 | 平台底座 | accounts.ts L23 |
| DB 連線池（HikariCP） | 依 Q4 容量設定 | int | 連線池大小（待 EDD 定稿） | 平台底座 | PRD §5.5 邊界 / §8.2 |
| Tracing 採樣率 | Prod 10% / Staging 100%（錯誤 100%） | % | OpenTelemetry 採樣 | 平台底座 | PRD §7.7.3 |

---

## §12 Spring Modulith 有界上下文（7 BC 模組名與邊界歸屬）

> 7 個有界上下文 = 7 個 Spring Modulith module；module = bounded context；跨模組只走 Public API 或 Domain Event（Transactional Outbox），無跨 BC DB FK、無共享可變狀態（HC-1~HC-5）。

| # | 模組名（BC） | 職責 | 獨佔列舉 / 常數 | 權威資料表 | 來源 |
|---|------------|------|---------------|-----------|------|
| 1 | `member` | 身分權威：註冊/登入/登出/我的資料（scrypt + session） | `SESSION_TTL_DAYS`、`SCRYPT_*`、`DISPLAY_NAME_*`、`PASSWORD_MIN` | accounts、sessions | PRD §5.1 / schema.sql L4-18 |
| 2 | `wallet` | 伺服器權威錢包：topup / spend，append-only ledger | currency、reason 列舉、`TOPUP_*`、`SPEND_*` | wallets、wallet_ledger | PRD §5.2 / schema.sql L21-39 |
| 3 | `deposit` | 儲值接口：假付費走 ledger，Phase B 真金流冪等/對帳預留 | `GEM_PACK.*`、`CAP_PACK.*`、包加量倍率 | （經 wallet 入帳） | PRD §4.1 / accounts.ts L10-18 |
| 4 | `entitlement` | 已購權益：原子扣款+授予 / 列出，破產不消失 | kind 列舉（cosmetic/dream/scenario） | entitlements | PRD §5.3 / schema.sql L42-49 |
| 5 | `record` | 經營史記錄：破產/成就/傳承（append-only） | type 列舉（bankruptcy/achievement/legacy） | records | PRD §4.1 / schema.sql L61-68 |
| 6 | `leaderboard` | 全域排行：歸戶寫入 + 公開 Top N 查詢 | `LEADERBOARD_LIMIT_*`、mode | leaderboard | PRD §4.1 / schema.sql L71-81 |
| 7 | `game-save` | 跨裝置存檔：/save GET / PUT（slot upsert） | `save slot 預設=main`、`REQUEST_BODY_MAX` | saves | PRD §5.4 / schema.sql L52-58 |

> **平台底座（非 BC，橫切）：** 承載 7 BC 的 Spring Modulith 單體 + Clean Architecture 4 層 + Day1 HA（≥2 replicas、HPA、PodDisruptionBudget）+ Event Publication Registry（Outbox）。相關常數見 §8 容量與 §4 SLO。

---

## Appendix A：constants.json 同步格式

本文件生成後同步輸出 [`docs/constants.json`](constants.json)，供下游 AI / 工具機器可讀引用。數值以 JSON 數字表示，列舉以字串陣列表示，`TBD` 記為 `null`。節錄結構如下：

```json
{
  "version": "1.0",
  "generated_from": ["docs/req/schema.sql", "docs/req/server-src/accounts.ts", "client/index.html"],
  "last_updated": "2026-10-30",
  "core": {
    "SESSION_TTL_DAYS": 30,
    "SCRYPT_KEYLEN": 64,
    "WALLET_INIT_CASH": 0,
    "WALLET_INIT_GEMS": 0,
    "GAME_INIT_CASH": 20000,
    "MAX_WORKERS": 8,
    "MIN_WORKERS": 1,
    "BANKRUPT_CASH": -15000,
    "RESCUE_GEMS_COST": 20,
    "INJECT_CASH": 20000
  },
  "thresholds": {
    "DISPLAY_NAME_MIN": 1,
    "DISPLAY_NAME_MAX": 24,
    "PASSWORD_MIN": 6,
    "TOPUP_AMOUNT_MIN": 1,
    "TOPUP_AMOUNT_MAX": 10000000,
    "REQUEST_BODY_MAX": 4000000,
    "LEADERBOARD_LIMIT_DEFAULT": 20,
    "LEADERBOARD_LIMIT_MAX": 100
  },
  "slo": {
    "availability_pct": 99.9,
    "p99_latency_ms": 1000,
    "error_rate_pct": 1,
    "ha_rolling_error_rate_pct": 0.1,
    "write_tps_min": 200
  },
  "capacity": {
    "max_concurrent_users_launch": 500,
    "max_concurrent_users_growth": 3000,
    "dau_launch": 5000,
    "dau_growth": 50000,
    "min_replicas": 2,
    "log_retention_hot_days": 90,
    "log_retention_cold_days": 365
  },
  "enums": {
    "currency": ["cash", "gems"],
    "wallet_ledger_reason": ["init", "topup", "rescue", "buy_cosmetic", "buy_dream", "buy_scenario"],
    "entitlement_kind": ["cosmetic", "dream", "scenario"],
    "record_type": ["bankruptcy", "achievement", "legacy"]
  },
  "error_codes": {
    "400": "請求格式/參數錯誤",
    "401": "未授權",
    "402": "餘額不足",
    "404": "找不到",
    "409": "衝突（已存在/已擁有）",
    "500": "伺服器錯誤"
  },
  "deposit_packs": {
    "GEM_PACK": {"g_small": 60, "g_mid": 330, "g_big": 700},
    "CAP_PACK": {"cap_1000": 1000}
  },
  "modulith_bounded_contexts": ["member", "wallet", "deposit", "entitlement", "record", "leaderboard", "game-save"],
  "config_keys": {
    "SESSION_TTL_DAYS": 30,
    "CORS_ALLOW_ORIGIN": "*",
    "CORS_ALLOW_METHODS": "GET,POST,PUT,OPTIONS"
  }
}
```
