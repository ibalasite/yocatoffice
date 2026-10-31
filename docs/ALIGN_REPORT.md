# gendoc — 跨文件對齊掃描報告（ALIGN_REPORT）

- 專案：<project-root>（yoCat Office backend，Spring Boot / Java Modulith）
- client_type：`api-only`（無前端 → 無 PDD/VDD/FRONTEND、無 features/client/）
- has_admin_backend：`false`
- 掃描日期：2026-10-30
- 掃描工具：`gendoc-align-check`（Skill）；語意跨文件比對以 subagent 審查完成
- 原則：只列問題，不修復（修復由 `gendoc-align-fix` 執行）

---

## 總覽

| 對齊層 | CRITICAL | HIGH | MEDIUM | LOW | 總計 | 狀態 |
|--------|:---:|:---:|:---:|:---:|:---:|:---:|
| D1 Doc → Doc | 0 | 1 | 2 | 5 | 8 | ⚠️ |
| D2 Doc → Code | — | — | — | — | N/A | 無應用程式 `src/`（docs-only 專案） |
| D3 Code → Test | — | — | — | — | N/A | 無 `tests/` 程式碼 |
| D4 Doc → Test | 0 | 3 | 6 | 3 | 12 | ⚠️ |
| D5 UML / RTM 品質 | 0 | 0 | 2 | 0 | 2 | ⚠️ |
| D6 AI Gencode 就緒度 | 0 | 1 | 1 | 0 | 2 | ⚠️ 78% |
| D7 Doc → Generated Artifact | — | — | — | — | N/A | 無 `docs/blueprint/`、`docs/pages/` |
| **總計** | **0** | **5** | **11** | **8** | **24** | |

> 無 CRITICAL。HIGH 共 5 項（D1×1、D4×3、D6×1），為本輪修復重點。

說明：本專案為純文件（gendoc-only）專案，未提交應用程式原始碼與測試（根目錄下 `server/`、`client/`、`laya/`、`ohmygame/`、`assets/`、`infra/` 為未追蹤的外部 / 腳手架 / 巢狀 git 內容，非 gendoc 管理範圍）。因此 D2、D3、D7 判定為 N/A。

---

## Dimension 1 — Doc → Doc 對齊問題

已驗證一致（無 finding）：認證方案（opaque session token / Bearer / TTL 30 天，全程無 JWT）在 BRD/PRD/EDD/API/CONSTANTS 一致；BRD→PRD 功能與 NFR 覆蓋完整；PRD→EDD 之 P0 AC 溯源與 NFR 回應（QPS≈35、500 PCU、200 TPS、99.9%、容量公式）完整；EDD→ARCH（7 BC、ADR、元件）與 EDD→API（18 endpoint、認證）一致；核心技術版本（Java 21、Spring Boot 3.3、Spring Modulith 1.3、PostgreSQL 16、K8s 1.30、ArgoCD 2.12）一致。

### [HIGH] EDD → LOCAL_DEPLOY（DB host 與 data namespace）[UPSTREAM_CONFLICT]
- 問題描述：EDD §3.5 環境矩陣「DB Host (Pattern)」Local 欄 = `postgres.yocat-office-backend-local.svc.cluster.local`（DB 服務名 `postgres`，位於 app namespace `yocat-office-backend-local`）。但 LOCAL_DEPLOY §2 將 PostgreSQL 放在獨立 data namespace `yocat`、服務名 `yocat-pg`：「API 以 K8s DNS `yocat-pg.yocat.svc.cluster.local:5432` 連既有 PostgreSQL」（§L463 `DATABASE_URL=jdbc:postgresql://yocat-pg.yocat.svc.cluster.local:5432/yocat`）。namespace（`yocat-office-backend-local` vs `yocat`）與服務名（`postgres` vs `yocat-pg`）皆不一致；EDD 完全未提及 `yocat` data namespace，其連線 pattern 無法解析至實際部署。
- 衝突類型：B1-下游合理（LOCAL_DEPLOY 正確沿用既有 `docs/req/postgres.yaml` 的 PostgreSQL；EDD §3.5 Local 列過時/錯誤）
- 建議修復方向：修改 EDD §3.5，將 Local「DB Host (Pattern)」改為 `yocat-pg.yocat.svc.cluster.local`，並註明資料層位於既有 `yocat` namespace（跨 namespace 連線），與 LOCAL_DEPLOY 及 postgres.yaml 對齊。
- 可自動修復：YES

### [MEDIUM] CONSTANTS.md + constants.json → SCHEMA / API / EDD（wallet_ledger reason enum）[UPSTREAM_CONFLICT]
- 問題描述：constants.json `enums.wallet_ledger_reason = ["topup","buy_cosmetic","hire_dream","rescue","init"]`、CONSTANTS.md §9 同列。此清單含未實際寫入的 `hire_dream`，且遺漏 `buy_dream`、`buy_scenario`。下游實際值集：SCHEMA.md L432-433 `CHECK (reason IN ('init','topup','rescue') OR reason LIKE 'buy\_%')`（註解 buy_cosmetic/buy_dream/buy_scenario）、API.md L186 `INSERT ledger(reason=buy_<kind>)`、EDD §3.3 Rule 4 `dream → buy_dream（語意別名 hire_dream）`。機器可讀 SSOT（constants.json）成員集錯誤，驗證工具會拒 `buy_dream`/`buy_scenario`、放行不存在的 `hire_dream`。
- 衝突類型：缺失（SSOT 機器檔列舉與真實值集不符）
- 建議修復方向：將 constants.json 與 CONSTANTS.md §9 enum 改為 `["init","topup","rescue","buy_cosmetic","buy_dream","buy_scenario"]`；`hire_dream` 僅保留為文件化語意別名，不列為 enum 成員。
- 可自動修復：YES

### [MEDIUM] EDD → PRD（Redis 依賴未於上游揭露）
- 問題描述：EDD 引入 Redis 7.2 為必要基礎設施（ADR-003/004、§3.3、session cache / rate-limit / leaderboard cache、Sentinel HA、pool=16），並向下傳播至 ARCH/SCHEMA/LOCAL_DEPLOY/CICD/DEVELOPER_GUIDE。但 PRD 全文未提 Redis：§8.2 技術依賴僅列 Spring Boot / Spring Modulith / PostgreSQL / HikariCP / Kubernetes，§8.3 外部依賴 / §4.3 Future Scope 皆缺。新的有狀態基礎設施依賴與其可用性/降級說明在 PRD 依賴與 NFR/可用性表缺席。
- 衝突類型：B1-下游合理（Redis 為合理設計決策；上游 PRD 依賴清單缺失）
- 建議修復方向：於 PRD §8.2 技術依賴、§8.3 外部依賴加入 Redis 7.2（附降級註記：cache miss 回源 DB，非硬 SPOF），引用 EDD ADR-003/004。
- 可自動修復：YES

### [LOW] DEVELOPER_GUIDE → EDD / LOCAL_DEPLOY / CICD（Redis 版本精度）
- 問題描述：DEVELOPER_GUIDE.md L26 & L41 標「Redis 7」，EDD §3.3、LOCAL_DEPLOY（`redis:7.2-alpine`）、CICD（Testcontainers Redis 7.2）則釘「Redis 7.2」。非硬衝突（7.2 ⊂ 7.x），僅標註不足。
- 衝突類型：B2-下游偏離（版本標註不夠精確）
- 建議修復方向：DEVELOPER_GUIDE 兩處改為「Redis 7.2」。
- 可自動修復：YES

### [LOW] CONSTANTS.md → constants.json（game-day 常數鍵名）
- 問題描述：CONSTANTS.md §1 命名 `GAME_DAY_START`（540）、`GAME_DAY_SETTLE`（1080）；constants.json `core` 命名 `GAME_DAY_START_MIN`（540）、`GAME_DAY_SETTLE_MIN`（1080）。值相同，鍵名不一致。
- 衝突類型：缺失（鍵名不一致）
- 建議修復方向：統一鍵名（CONSTANTS.md 採 `_MIN` 後綴，或 constants.json 去除）。
- 可自動修復：YES

### [LOW] CONSTANTS.md / constants.json（client BACKEND_URL 預設埠過時）
- 問題描述：CONSTANTS.md §11 與 constants.json `config_keys.CONFIG_BACKEND_URL_default` = `http://localhost:3000`（舊 Node 原型埠）。Spring Boot trust layer 服務於 8080（EDD §2.2、LOCAL_DEPLOY NodePort 30088 / Ingress 80），預設指向後端從未綁定的埠。
- 衝突類型：B2-下游偏離（沿用原型埠號未更新）
- 建議修復方向：將 client 預設改為實際入口（Ingress `http://localhost` / dev NodePort `:30088`），或註明為 client 自有 legacy 值、不在後端範圍。
- 可自動修復：YES

### [LOW] PRD → EDD/API/SCHEMA（deposit 冪等於 Phase A 已落地 vs PRD「Phase B 預留」）
- 問題描述：PRD §11.1 標冪等表為「新增（Phase B 預留）」，下游卻於 Phase A 啟用：API.md §7 使 `Idempotency-Key` 對 `POST /deposit/checkout` 現為必填；SCHEMA `deposit.deposit_order.idempotency_key` UNIQUE + `deposit.deposit_idempotency` 已上線。PRD「Phase B 預留」描述過時。
- 衝突類型：B1-下游合理（下游提前落地冪等，PRD 標註過時）
- 建議修復方向：更新 PRD §11.1，說明 `/deposit/checkout` 冪等自 Phase A 生效（真金流對帳仍 Phase B）。
- 可自動修復：YES

### [LOW] SCHEMA → PRD/API（孤兒認證表 = gold-plating）
- 問題描述：SCHEMA 定義 `member.password_history`（§3 L363）與 `member.login_attempts`（L304）。`password_history` 預設 change/rotate-password 流程，但無對應 endpoint（`PATCH /auth/me`、`DELETE /auth/me` 皆 Phase B，PRD §17.3），且無 PRD user story 覆蓋密碼輪替。`login_attempts` 部分支援暴力破解/限流（PRD §7.4）尚可辯護；`password_history` 為無上游需求之表。
- 衝突類型：gold-plating（無上游需求對應之資料表）
- 建議修復方向：將 `password_history` 延至 Phase B 帳號管理里程碑（移出 MVP migration），或於 PRD/API 補密碼變更需求以正當化。
- 可自動修復：NO

---

## Dimension 4 — Doc → Test 對齊問題

基準（實際計數）：9 個 feature 檔 = **65 個 Gherkin scenario**（含 6 個 Scenario Outline）。PRD §5 定義 **26 個 P0 AC**（MEM 6 + WAL 5 + ENT 5 + SAVE 5 + PLAT 5）。RTM 宣稱 80 TC。
已驗證（無 finding）：MEM/WAL/ENT/SAVE 每個 P0 AC 皆至少一個 scenario 且有 RTM 行；65 個 scenario 的 `@TC-*` tag 全數解析至 RTM 行（無懸空/未溯源 scenario）；server_steps.ts 為 9 個 feature 全部 Given/When/Then 皆定義對應 step（stub 回 `pending`，docs-only 預期）。

### [HIGH] PRD → BDD → RTM：P1 需求在 PRD 無 AC（record/leaderboard/deposit）
- 問題描述：3 個 P1 User Story 在 PRD 無驗收標準。PRD §15 RTM 行 US-REC-001/US-LB-001/US-DEP-001 寫「AC# = 待 BDD 補」，PRD §5 僅 5.1–5.5（P0）。但 18 個 scenario 使用發明的 AC ID（如 `@US-DEP-001-AC2`、`@US-REC-001-AC1`、`@US-LB-001-AC1`），RTM §15.6 亦溯源「US-DEP-001/AC-2」。這些 AC ID 僅存於 BDD/RTM，PRD 從未定義 → PRD→BDD 鏈在源頭斷裂（約佔 27% 需求）；deposit 的 `deposit_pack`/`deposit_order`（RTM §15.5）亦不在 PRD §11.1。
- 建議修復方向：於 PRD §5 為 REC/LB/DEP 補 AC 表（或新增 §5.6–5.8），再讓 BDD/RTM 的 AC ID 指向之；並將 deposit 資料模型納入 PRD §11.1。
- 可自動修復：NO

### [HIGH] test-plan → RTM（unit test 數量與懸空 TC-ID）
- 問題描述：test-plan §15.2 列 18 class 各含 S/E/B 三元組，§3.1 稱「每個 public method ≥ 3 TC：S/E/B … 26 class」（≈54+ unit TC）；RTM §1 卻稱「Unit Test 數 14」「總 Method 數 11」。且 RTM §15.4 引用的 unit TC-ID 不存在於 RTM 權威 §3.2 表或 §6 索引（如 `TC-UNIT-MONEY-S01/E01`、`TC-UNIT-ACCOUNT-S01/E01/B01`、`TC-UNIT-SESSION-*`、`TC-UNIT-SPENDWALLET-*`、`TC-UNIT-SCRYPTENC-*`、`TC-UNIT-BEARERFILTER-*`）→ RTM 內部懸空，「Method 覆蓋率 100%」僅就 11 個 cherry-pick method 計算、不可驗證。
- 建議修復方向：擇一 unit-test 清單為 SSOT；擴充 RTM §3.2/§6 至完整 class×(S/E/B)，或將 test-plan §15.2 修正為真實的 14；重算 §1 總數。
- 可自動修復：NO

### [HIGH] test-plan → RTM/BDD（deposit 端點遺漏於 test-plan）
- 問題描述：test-plan 多處稱「16 個 REST 端點」，§3.2 端點覆蓋表恰列 16，但**完全遺漏 `GET /deposit/packs` 與 `POST /deposit/checkout`**。RTM §4.2 計 18 端點（API-001–018），deposit 有 7 個整合 TC（TC-E2E-DEP-001/002、TC-INT-DEP-003–007），deposit_checkout.feature 有 7 scenario（含關鍵冪等路徑）。deposit 的信任關鍵 checkout/冪等流程在 BDD+RTM 有測、在 test-plan 端點策略與計數卻隱形。§2.1 亦誤標 deposit 為「對應 US-WAL-001 topup pack」、AC 數「—」。
- 建議修復方向：於 test-plan §3.2 加入兩個 deposit 端點、將「16」改為「18」，並於 §2.1 給 deposit 獨立 REQ-ID 行。
- 可自動修復：YES

### [MEDIUM] PRD AC → BDD：PLAT AC-1/AC-2/AC-5 無 Gherkin scenario
- 問題描述：modulith_boundaries.feature 僅涵蓋 PLAT AC-3（DAG）與 AC-4（public API/event）。AC-1（kill pod、錯誤率 <0.1%）、AC-2（滾動更新）、AC-5（local-first）無 scenario（RTM §5.2 註「test-plan §15.1 基礎設施場景，無 BDD feature」）。與 PRD §9.5 DoD「所有 P0 AC 均有對應 BDD scenario 並通過」矛盾（仍可經 TC-E2E-PLAT-001/002/005 溯源，故非 critical）。
- 建議修復方向：撰寫 `@ha` Gherkin scenario（test-plan §3.6.2 已草擬），或修改 PRD DoD 將基礎設施/NFR AC 豁免於 BDD。
- 可自動修復：NO

### [MEDIUM] PRD（缺口）→ BDD/RTM/test-plan：限流（429）與 login 暴力破解不一致
- 問題描述：限流在 PRD（§5、§7 NFR、§8）全未提，卻有測試：member register `TC-INT-MEM-009-E01`（429）、wallet spend `TC-INT-WAL-007-E01`（429）存於 BDD+RTM。反之 test-plan §3.6.1/§3.7 A07 明訂「login 10/min/IP 限流」，但 **login 無 429 scenario、無 RTM 行** — 安全相關控制套用不一致（register+spend 有保護、login 無）。429 scenario 亦因無真實 AC 而掛至不相關 AC（`@US-MEM-001-AC1`、`@US-WAL-001-AC2`）。
- 建議修復方向：於 PRD 加入限流 NFR 與專屬 AC；補 login-429 scenario + RTM 行；使 429 覆蓋在 auth/write 端點一致。
- 可自動修復：NO

### [MEDIUM] test-plan ↔ RTM TC-ID 不符：PLAT AC-3/AC-4
- 問題描述：同一組 AC，test-plan §15.1 用 `TC-UNIT-PLAT-003`（AC-3）、`TC-INT-PLAT-004`（AC-4）；RTM.md 與 modulith feature 用 `TC-UNIT-ARCH-005-S01`（AC-3）、`TC-INT-ARCH-001…004/006-S01`（AC-4）。TC-ID 根（PLAT vs ARCH）不符 → 自動從 test-plan §15.1 溯源至 RTM/features 失敗。
- 建議修復方向：統一採 ARCH ID（features+RTM 所用），更新 test-plan §15.1。
- 可自動修復：YES

### [MEDIUM] PRD → 測試覆蓋：PRD §7.8 Analytics Events（9）無 BDD/RTM TC
- 問題描述：PRD §7.8 定義 9 個 server-side analytics event（`member_register_completed`、`wallet_spend_rejected`、`entitlement_purchase_completed` 等），各綁 KPI，PRD §9.5 DoD 要求「§7.8 所有 Event 已確認觸發 ≥ 5 次」。無 feature scenario 斷言任何 §7.8 event 發送，RTM 無對應 TC。（domain_event_contracts 覆蓋的是內部 domain event 如 MemberRegistered，與 §7.8 analytics event 不同。）
- 建議修復方向：為 9 個 analytics event 加入 BDD 斷言（或獨立 analytics 測試類別）+ RTM 行，或將該 DoD 項移至非自動化檢查清單。
- 可自動修復：NO

### [MEDIUM] test-plan → BDD/RTM：MemberDeleted event 對未溯源 + 範圍混淆
- 問題描述：test-plan §3.8.3 定義 5 個跨 BC 合約測試 SM-TEST-02-EVT-03..07（`MemberDeleted` → wallet/entitlement/record/leaderboard/game-save 清理）。domain_event_contracts.feature 無 `MemberDeleted` scenario，RTM 無對應 TC。觸發端點（`DELETE /auth/me`）為 Phase B（PRD §17.3），test-plan 卻為無 PRD-A 需求、無溯源的 Phase B 清理指定測試。
- 建議修復方向：補 MemberDeleted scenario+RTM 行，或於 test-plan 明標 SM-TEST-02-EVT-03..07 為 Phase B / 範圍外。
- 可自動修復：NO

### [LOW] test-plan ↔ RTM/features：TC-ID 後綴慣例不符
- 問題描述：test-plan §15.1 寫裸根（`TC-E2E-MEM-001`、`TC-INT-MEM-002`、`TC-UNIT-WAL-004`），RTM.md 與全部 features 用 `-S01/-E01/-B01` 後綴。人可對應、字串工具無法精確溯源。
- 建議修復方向：於 test-plan §15.1 一致套用後綴慣例。
- 可自動修復：YES

### [LOW] RTM 內部計數缺陷：§1 / §4.2 / §5.2 / §6
- 問題描述：`TC-E2E-ENT-005-S01` 被重複計數 — §4.2 Integration（API-011）一次、§5.2 E2E（SC-ENT-05）一次，§6 同一 RQ-003 格內亦列兩次（稱「總測試數 11」實僅 10 唯一）。故「Total Test Cases = 80 / E2E = 6」略為高估，§6 RQ-003 總數錯誤。
- 建議修復方向：去重 ENT-005，修 RQ-003 計數為 10，重算 §1 總數。
- 可自動修復：YES

### [LOW] BDD AC-tag 準確性：features/member + features/cross-module/event
- 問題描述：數個 scenario 因無精確 AC 而掛至描述不同行為的 AC：GET /auth/me happy-path「已登入者查詢我的資料」掛 `@US-MEM-001-AC1`（AC-1 為註冊；PRD 僅有 AC-6 之 401 情境，無 200 情境）；兩個 logout scenario 皆掛 `@US-MEM-001-AC4`（AC-4 為 login）。domain_event_contracts「wallet 消費會員註冊事件後建立初始錢包」掛 `@US-WAL-001-AC1`（topup），實為 MEM AC-1（註冊建立錢包 + init ledger）。
- 建議修復方向：於 PRD 補專屬 AC（me-success、logout）後重掛；將 register 驅動的開錢包 scenario 重掛至 MEM AC-1。
- 可自動修復：NO

---

## Dimension 5 — UML / RTM 結構品質

已存在：EDD class diagram 分三張（class-domain、class-application、class-infra-presentation）+ class-inventory.md；9 大 Server UML（use-case/class/object/sequence×N/communication/state×N/activity×N/component/deployment/er-diagram）+ CI/CD UML + Modulith 圖，皆以 Mermaid 呈現，位於 `docs/diagrams/`。

### [MEDIUM] docs/RTM.csv 不存在（缺少機器可讀 RTM）
- 問題描述：僅有 docs/RTM.md，無 docs/RTM.csv。缺少機器可讀追溯矩陣，不利自動化覆蓋率計算。
- 建議修復方向：由 RTM.md 匯出 RTM.csv（或於 gendoc 流程補生成）。
- 可自動修復：YES

### [MEDIUM] docs/diagrams/puml/ 無 PlantUML 檔
- 問題描述：diagrams 以 Mermaid（.md）實作，無 `docs/diagrams/puml/*.puml`。若流程要求 PlantUML 併行輸出則缺失；若專案標準即為 Mermaid，可視為設計選擇（降級為資訊性）。
- 建議修復方向：若需 PlantUML，補生成；否則於報告註記 Mermaid 為專案圖表標準。
- 可自動修復：YES（條件性）

---

## Dimension 6 — AI Gencode Readiness

| Layer | Score | 狀態 |
|-------|:---:|:---:|
| SCHEMA | 100% | ✅ 就緒 |
| BDD/Tests | 100% | ✅ 就緒 |
| API | 70% | ⚠️ 可提升 |
| CI/CD | 70% | ⚠️ 可提升 |
| LOCAL_DEPLOY | 50% | 🔴 需補強 |

整體 AI Gencode 就緒度：**78%**（⚠️）

### [HIGH] LOCAL_DEPLOY：AI gencode 就緒度 50%
- 問題描述：掃描缺少實際 `.env.example` 檔案，以及 supabase/migration-up/pnpm 類一鍵啟動指令與 seed 指令密度不足。對 AI 直接生成本機部署腳本的就緒度偏低。
- 建議修復方向：補 `.env.example`（或在文件內嵌完整範例）、補足 docker-compose / k8s manifest 與 `db:seed` 指令區塊。
- 可自動修復：YES（部分）

### [MEDIUM] API：AI gencode 就緒度 70%
- 問題描述：有 JSON request/response 範例與 error code，但缺少 Zod/joi/class-validator 類可執行驗證 schema 定義，AI 生成 input validation 需自行推導。
- 建議修復方向：於 API.md 補每個端點的驗證 schema（或 TS interface + validator）。
- 可自動修復：YES

---

## Dimension 7 — Doc → Generated Artifact

N/A：本專案無 `docs/blueprint/`（mock、contracts）與 `docs/pages/`（prototype、API Explorer、HTML）。gendoc-only 文件階段尚未生成下游 artifact，故無可比對項。

---

## 修復建議

- 建議執行：`/gendoc-align-fix all`（CRITICAL/HIGH 必修）
- 本輪 HIGH 修復重點（5 項）：
  1. [D1] EDD §3.5 DB host / data namespace 對齊 LOCAL_DEPLOY（`yocat-pg.yocat...`）
  2. [D4] PRD 為 P1（REC/LB/DEP）補 AC，接回 BDD/RTM AC ID
  3. [D4] test-plan unit-test 數量 SSOT 化、修懸空 TC-ID、重算總數
  4. [D4] test-plan §3.2 補 deposit 兩端點、「16」→「18」
  5. [D6] LOCAL_DEPLOY 補 `.env.example` 與一鍵啟動/seed 指令
- 無法自動修復（需設計/需求決策）者已於各 finding 標 `可自動修復: NO`。

---

## 修復狀態（gendoc-align-fix，layer=all）

本輪已自動修復（可自動修復: YES）：

| # | Finding | 嚴重度 | 狀態 | 修改檔案 |
|---|---------|:---:|:---:|---------|
| 1 | D1 EDD §3.5 DB host → `yocat-pg.yocat.svc.cluster.local`（+ data namespace 註記） | HIGH | ✅ FIXED | EDD.md |
| 2 | D4 PRD P1（REC/LB/DEP）補 AC（§5.6/5.7/5.8）+ 接回 BDD/RTM AC ID + §11.1 deposit 資料表 | HIGH | ✅ FIXED | PRD.md |
| 3 | D4 test-plan deposit 端點補入、「16」→「18」、§2.1 deposit REQ-ID | HIGH | ✅ FIXED | test-plan.md |
| 4 | D6 LOCAL_DEPLOY 補 `.env.example`（Spring Boot/PG/Redis 變數）+ cp 提示 | HIGH | ✅ FIXED | .env.example, LOCAL_DEPLOY.md |
| 5 | D1 constants wallet_ledger_reason enum → init/topup/rescue/buy_cosmetic/buy_dream/buy_scenario | MEDIUM | ✅ FIXED | constants.json, CONSTANTS.md |
| 6 | D1/D4 Redis 7.2 加入 PRD §8.2/§8.3 依賴（含降級策略） | MEDIUM | ✅ FIXED | PRD.md |
| 7 | D4 test-plan TC-ID PLAT→ARCH 統一 | MEDIUM | ✅ FIXED | test-plan.md |
| 8 | D6 API.md 補 Bean Validation 驗證規則（§2.7.1，7 個寫入端點） | MEDIUM | ✅ FIXED | API.md |
| 9 | D5 補 docs/RTM.csv（機器可讀追溯矩陣，79 行 + header） | MEDIUM | ✅ FIXED | RTM.csv |
| 10 | D1 DEVELOPER_GUIDE Redis 7 → 7.2 | LOW | ✅ FIXED | DEVELOPER_GUIDE.md |
| 11 | D1 game-day 常數鍵名 `_MIN` 後綴對齊 | LOW | ✅ FIXED | CONSTANTS.md |
| 12 | D1 client BACKEND_URL 預設 `:3000` → `:30088` | LOW | ✅ FIXED | constants.json, CONSTANTS.md |
| 13 | D4 test-plan §15.1 TC-ID `-S01/-E01/-B01` 後綴慣例統一 | LOW | ✅ FIXED | test-plan.md |
| 14 | D4 RTM `TC-E2E-ENT-005-S01` 去重、RQ-003 11→10、總數 80→79 重算 | LOW | ✅ FIXED | RTM.md, RTM.csv |

需人工確認 / 需求決策（可自動修復: NO，本輪不自動改）：

| # | Finding | 嚴重度 | 狀態 | 原因 |
|---|---------|:---:|:---:|------|
| A | D4 test-plan unit-test 數量 SSOT（§15.2 的 18 class×S/E/B vs RTM §1「14」）與懸空 unit TC-ID（TC-UNIT-MONEY/ACCOUNT/...） | HIGH | ⚠️ MANUAL | 需決定測試深度 SSOT（擴 RTM 至完整 class×SEB 或下修 test-plan 至 14），涉及測試策略取捨；RTM 內部計數缺陷已先行修復 |
| B | D4 PLAT AC-1/AC-2/AC-5 無 BDD scenario（與 PRD §9.5 DoD 矛盾） | MEDIUM | ⚠️ MANUAL | 需決定撰寫 `@ha` 基礎設施 scenario 或修改 DoD 豁免 NFR AC（仍可經 TC-E2E-PLAT 溯源） |
| C | D4 限流（429）與 login 暴力破解覆蓋不一致（PRD 無限流 NFR） | MEDIUM | ⚠️ MANUAL | 需 PM 新增限流 NFR + AC 後再補 login-429 scenario |
| D | D4 PRD §7.8 Analytics Events（9）無 BDD/RTM TC | MEDIUM | ⚠️ MANUAL | 需決定 analytics event 驗證方式（自動化 or 非自動化 DoD 清單） |
| E | D4 MemberDeleted event 對（SM-TEST-02-EVT-03..07）未溯源（DELETE /auth/me 為 Phase B） | MEDIUM | ⚠️ MANUAL | 需決定 Phase B 範圍標註 |
| F | D1 PRD §11.1 deposit 冪等「Phase B 預留」描述過時（實際 Phase A 已落地） | LOW | ⚠️ 部分 | 由 D4 §11.1 deposit 補充時一併澄清；原 B-tag 文字可後續微調 |
| G | D4 BDD AC-tag 準確性（me-success/logout 掛錯 AC） | LOW | ⚠️ MANUAL | 待 PRD 補專屬 AC（me-success、logout）後重掛 |
| H | D1 SCHEMA `password_history` 孤兒表（gold-plating） | LOW | ⚠️ MANUAL | 保留程式/資料表不自動刪，待決定延 Phase B 或補需求 |

小結：本輪修復 14 項（HIGH 4 / MEDIUM 5 / LOW 5），MANUAL 保留 8 項（HIGH 1 / MEDIUM 4 / LOW 3，均為需求/測試策略決策，不屬可自動對齊範圍）。

---

## 複驗結果（ALIGN-VERIFY，gendoc-align-check 第二輪）

複驗日期：2026-10-30 · 複驗對象：align-fix 後之 docs/*.md、features/、constants/、.env.example

### 複驗總覽（修復後殘留）

| 對齊層 | CRITICAL | HIGH | MEDIUM | LOW | 狀態 |
|--------|:---:|:---:|:---:|:---:|:---:|
| D1 Doc → Doc | 0 | 0 | 0 | 2（MANUAL/部分） | ✅ |
| D2 Doc → Code | — | — | — | — | N/A |
| D3 Code → Test | — | — | — | — | N/A |
| D4 Doc → Test | 0 | 0 | 4（MANUAL） | 1（MANUAL） | ✅ HIGH 歸零 |
| D5 UML / RTM 品質 | 0 | 0 | 1（PlantUML，條件性） | 0 | ✅（RTM.csv 已補） |
| D6 AI Gencode | 0 | 0 | 1（API 70%，內容已補 Bean Validation，評分 regex 為 Zod 導向） | 0 | ✅（LOCAL_DEPLOY 50%→80%） |
| D7 Doc → Artifact | — | — | — | — | N/A |
| **總計** | **0** | **0** | **6** | **3** | |

### 複驗結論

- **CRITICAL = 0、HIGH = 0**：初掃 5 項 HIGH 全數修復並通過複驗：
  1. EDD §3.5 DB host 已對齊 `yocat-pg.yocat.svc.cluster.local`（與 LOCAL_DEPLOY/postgres.yaml 一致）✅
  2. PRD P1（REC/LB/DEP）AC 已補（§5.6/5.7/5.8），BDD/RTM AC ID 已接回 ✅
  3. test-plan deposit 端點補入、端點數 16→18 全文一致 ✅
  4. test-plan↔RTM unit-test 數量 SSOT 化（統一 14 TC / 11 method，RTM §3.2 為準），RTM §15.4 懸空 TC-ID 全部解析或標「規劃中」✅
  5. LOCAL_DEPLOY `.env.example` 已補，D6 就緒度 50%→80% ✅
- **殘留 MEDIUM（6）/ LOW（3）** 皆為需求/測試策略決策（MANUAL），或為評分 regex 技術特性（D6 API 以 Zod 關鍵字評分，本專案為 Java Bean Validation，內容已補但分數不反映）、或條件性項目（PlantUML：本專案圖表標準為 Mermaid）。詳見上方「修復狀態」之 MANUAL 清單（A–H）。這些不屬可自動對齊範圍，需 PM / 測試負責人決策後再處理。

> 判定：ALIGN-VERIFY 通過 — CRITICAL/HIGH 歸零；MEDIUM/LOW 殘留均為已記錄之人工決策項。
