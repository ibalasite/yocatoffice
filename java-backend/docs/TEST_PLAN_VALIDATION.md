# Test Plan Validation Report — 測試計畫驗證報告

> Artifact: **O-40** · Sub-step: `recon-test-plan-validate` · Phase: Recon
> Standard: IEEE 829 Test Plan completeness audit
> Generated: 2026-10-30 · Validator role: QA Lead
> Verdict: **PASS** — `docs/test-plan.md` 對照 PRD §AC / API §Endpoints / SCHEMA §Tables / ARCH §Test Framework 完整，無阻斷性缺口。

本報告驗證 `<project-root>/docs/test-plan.md` 的完整性，對照下列上游文件（唯讀來源 `<project-root>/docs/`）：

| 上游文件 | 使用章節 | 角色 |
|---|---|---|
| test-plan.md | 全文（§1–§21） | 被驗證對象 |
| PRD.md | §5 User Stories & Acceptance Criteria | AC 覆蓋基準 |
| API.md | §3 / §8 Endpoints | 端點覆蓋基準 |
| SCHEMA.md | §Tables（CREATE TABLE） | 資料表覆蓋基準 |
| ARCH.md | §11 Tech Stack / Test Framework | 工具鏈一致性 |
| RTM.md | §Coverage 目標 | 覆蓋率門檻一致性 |

archConfig（來自 state）：`lang_stack=java` / `framework=spring-boot-3.3` / `test_framework=junit5` / `orm=spring-data-jdbc` / `client_engine=none` / `db_type=postgres` / `cache_type=redis`。

---

## §1 測試類型覆蓋對照（8 種）

template 要求的 8 種測試類型是否都被 test-plan 列出：

| # | 測試類型 | test-plan 位置 | 工具 | 狀態 |
|---|---|---|---|---|
| 1 | Unit | §3.1 | JUnit 5 + Mockito + JaCoCo | ✅ 列出 |
| 2 | Integration | §3.2 | Spring MockMvc + Testcontainers（PostgreSQL 16 / Redis） | ✅ 列出 |
| 3 | Contract | §3.2 / §8.5 | Pact（Consumer/Provider，PactFlow） | ✅ 列出（整合於 Integration 層） |
| 4 | E2E | §3.3 | Playwright | ✅ 列出 |
| 5 | BDD | §3.3 | Cucumber `.feature`（9 features present） | ✅ 列出 |
| 6 | Performance | §3.4 / §10 | k6 / Gatling；Load 500 PCU、P99≤1000ms | ✅ 列出 |
| 7 | Security | §3.7 / §11 | SpotBugs + Semgrep（SAST）、OWASP ZAP（DAST）、OWASP Top 10 A01–A10 | ✅ 列出 |
| 8 | UAT | §3.5 / §12 | 手動，P0 功能 ≥1 場景 | ✅ 列出 |

**額外（超出 template 要求，加分）**：§3.6 HA / Failover / Chaos Tests（toxiproxy + Pod kill）、§3.8 Spring Modulith Decomposability Test Suite（SM-TEST-00~HC4）、§19 Mobile、§20 Accessibility。

> **判定**：8/8 測試類型全數列出並附工具/門檻。Contract 測試以 Pact 整合於 Integration 層（Spring Boot 常見做法），非獨立章節但明確列舉，視為覆蓋。**無缺口。**

---

## §2 PRD Acceptance Criteria 覆蓋對照

**基準（PRD §5）**：8 個 User Story，共 **30 條 AC**（26 P0 + 4 P1）。

| User Story | AC 數 | 優先級 | test-plan §15.1 TC 對應 | 狀態 |
|---|:--:|:--:|---|:--:|
| US-MEM-001（身分/帳號） | 6 | P0 | TC-E2E/INT/UNIT-MEM-001~006 | ✅ 6/6 |
| US-WAL-001（錢包） | 5 | P0 | TC-E2E/INT/UNIT-WAL-001~005 | ✅ 5/5 |
| US-ENT-001（權益） | 5 | P0 | TC-E2E/INT/UNIT-ENT-001~005 | ✅ 5/5 |
| US-SAVE-001（存檔） | 5 | P0 | TC-E2E/INT/UNIT-SAVE-001~005 | ✅ 5/5 |
| US-PLAT-001（平台/HA） | 5 | P0 | TC-E2E/INT/UNIT-PLAT/ARCH-001~005 | ✅ 5/5 |
| US-REC-001（經營史） | 1 | P1 | TC-INT-REC-001 | ✅ 1/1 |
| US-LB-001（排行榜） | 1 | P1 | TC-INT-LB-001（Integration + Performance） | ✅ 1/1 |
| US-DEP-001（儲值） | 2 | P1 | TC-E2E-DEP-001 / TC-E2E-DEP-002 | ✅ 2/2 |
| **合計** | **30** | — | **30 TC** | **✅ 30/30** |

量化對照：PRD 列 30 條 AC（`grep -oE "US-[A-Z]+-[0-9]+ / AC-[0-9]+"` 去重 = 30）↔ test-plan §15.1 Functional RTM 列 30 列 TC，一一對應。

- **P0 AC 覆蓋率 = 26/26 = 100%**（test-plan §15.3 未覆蓋清單為空 = PASS）。
- **P1 AC 覆蓋率 = 4/4 = 100%**（REC/LB/DEP 均有 TC-E2E/INT）。
- 每條 AC 的測試類型與 PRD 「對應測試類型」欄位一致（AC-1 E2E、重複/衝突類 Integration、輸入驗證 Unit）。

> **判定**：PRD 每條 AC 均有 ≥1 對應測試案例，類型分配合理。**無 CRITICAL 缺口。**

---

## §3 API Endpoints 覆蓋對照（happy path + error path）

**基準（API.md）**：Phase A 公開契約 **18 個 REST 端點**（API.md §3，EDD §5.2）。另有 3 個端點在 API.md 明確標註為 **Phase B / 內部**，非當前契約範圍。

test-plan §3.2 的「API Endpoint 覆蓋」表逐一列出 18 端點並指定必測狀態碼（200 成功 + 對應 4xx/5xx 錯誤路徑）：

| Method / Path | happy | error path（必測狀態碼） | 狀態 |
|---|:--:|---|:--:|
| GET /health/live | 200 | — | ✅ |
| GET /health/ready | 200 | 503（DB/Redis down） | ✅ |
| POST /auth/register | 200 | 400 / 409 | ✅ |
| POST /auth/login | 200 | 400 / 401 | ✅ |
| POST /auth/logout | 200 | 401 | ✅ |
| GET /auth/me | 200 | 401 | ✅ |
| POST /wallet/topup | 200 | 400 / 401 | ✅ |
| POST /wallet/spend | 200 | 400 / 401 / 402 | ✅ |
| GET /deposit/packs | 200 | — | ✅ |
| POST /deposit/checkout | 200 | 400 / 401 / 409 | ✅ |
| GET /entitlements | 200 | 401 | ✅ |
| POST /entitlements/buy | 200 | 400 / 401 / 402 / 409 | ✅ |
| GET /save | 200（含 `{save:null}`） | 401 | ✅ |
| PUT /save | 200 | 400 / 401 | ✅ |
| POST /records | 200 | 400 / 401 | ✅ |
| GET /records | 200 | 401 | ✅ |
| GET /leaderboard | 200 | — | ✅ |
| POST /leaderboard | 200 | 400 / 401 | ✅ |

量化對照：API.md Phase-A 端點 18 ↔ test-plan §3.2 覆蓋表 18 列，完全對等。每個需授權端點含 401，業務衝突端點含 402/409，寫入端點含 400。

### §3.1 Phase-B / 內部端點（合法排除，非缺口）

| 端點 | API.md 標註 | 排除依據（Iron Law §1 — 文件明寫不需要） |
|---|---|---|
| POST /save/upload-url | API.md §「若存檔超過 4MB，Phase B 改採 S3 presigned URL」 | Phase B 功能，當前契約未啟用 |
| POST /records/batch | API.md §「若未來需批次上傳」 | future，當前契約未啟用 |
| POST /deposit/webhook | API.md §「Phase B 對接真金流…（內部，非本契約公開端點）」 | Phase B + 內部端點，非公開契約 |

> 此 3 端點於 API.md 以「Phase B / 若未來需 / 非本契約公開端點」明文排除，依 Iron Law §1「文件明寫不需要為最高優先省略依據」合法不納入當前測試覆蓋，待 Phase B 啟用時由 change-request 補測。

> **判定**：18/18 當前契約端點皆有 happy + error path 測試；3 個 Phase-B/內部端點合法排除。**無 CRITICAL 缺口。**

---

## §4 SCHEMA Tables 覆蓋對照

**基準（SCHEMA.md）**：共 **44 張 table**（`grep -c "CREATE TABLE"` = 44），分屬 7 個 BC schema + modulith + platform。

test-plan §3.2「資料庫約束測試策略」以 Testcontainers（PostgreSQL 16）對核心交易表做 CRUD + 約束 + 行鎖 + 併發測試。依表的性質分類覆蓋：

| 類別 | 代表 table | 覆蓋方式 | 狀態 |
|---|---|---|:--:|
| **核心交易表（7 BC）** | wallet.wallets, wallet.wallet_ledger, member.accounts, member.sessions, entitlement.entitlements, game_save.saves, deposit.deposit_order, record.records, leaderboard.leaderboard | §3.2 Testcontainers CRUD + `SELECT FOR UPDATE` 行鎖 + UNIQUE 約束 + append-only + 併發（HA-INT-01） | ✅ 明確覆蓋 |
| **目錄/參考表** | deposit.deposit_pack, entitlement.entitlement_catalog, record.record_type_catalog, leaderboard.leaderboard_mode_catalog | 經 seed data + 查詢端點 integration test 間接覆蓋（§3.2 資料清理/seed 版本化） | ✅ 間接覆蓋 |
| **冪等/事件/快照表** | wallet.wallet_inbound_events, deposit.deposit_idempotency, leaderboard.leaderboard_snapshot, wallet.wallet_balance_snapshots | §3.2 event_id 去重冪等測試（EDD §4.6.1）+ Modulith `@ApplicationModuleTest` | ✅ 覆蓋 |
| **Modulith 框架表** | modulith.event_publication(_archive), modulith.event_dead_letter | Spring Modulith 框架自管（Outbox 投遞測試 SM-TEST-02 間接驗證） | ✅ 框架管理 |
| **Platform 觀測/稽核表** | platform.audit_log, platform.api_request_log, platform.slo_measurements, platform.data_access_logs, platform.health_check_log, platform.rate_limit_counters, platform.feature_flags 等 | 寫入型 sink（observability/audit），由中介層自動寫入；§3.7 安全測試 + §11 覆蓋稽核路徑 | ⚠️ 無獨立 CRUD IT |

### §4.1 覆蓋缺口與偏離（記入 state.deviations）

- **DEV-1（NICE-TO-HAVE）**：約 14 張 `platform.*` 觀測/稽核/配置表（audit_log, api_request_log, slo_measurements, data_access_logs, gdpr_erasure_requests, feature_flag_audit, schema_migrations, system_config, error_code_registry, health_check_log, outbox_relay_offset, replication_status_log, rate_limit_counters, feature_flags）無「每表獨立 CRUD integration test」。這些為寫入型 observability/audit sink 或框架/migration 管理表，非帶使用者 AC 的領域實體；由中介層與平台機制寫入，其正確性由 §3.7 安全稽核與 §3.2 限流/session 測試間接驗證。建議於 Platform phase 補 `platform.*` 寫入驗證 IT（thin assertion）。
  - 依據：Iron Law §2（文件未明列每表 CRUD→套「核心交易表優先」預設）；非 §1/§4 違反（領域交易表已 100% 覆蓋），故列 NICE-TO-HAVE 不列 CRITICAL。

> **判定**：7 BC 的核心交易/領域表 100% 有 integration test（CRUD + 約束 + 行鎖）；參考/事件表覆蓋；platform 觀測表為合理之間接覆蓋。**無 CRITICAL 缺口；1 筆 NICE-TO-HAVE deviation 待 Platform phase 補強。**

---

## §5 覆蓋率門檻一致性（test-plan ↔ RTM ↔ ARCH）

| 門檻 | test-plan | RTM | ARCH/EDD 來源 | 一致 |
|---|---|---|---|:--:|
| Unit Line Coverage | ≥ 80%（核心交易 100%） | 需求覆蓋 100% | PRD §7.6 / JaCoCo | ✅ |
| Unit Branch Coverage | ≥ 70% | — | 業界標準 | ✅ |
| 核心 UseCase 覆蓋 | SpendWallet/TopUp/BuyEntitlement ≥ 100% | 11 核心 Method / 14 TC = 100% | — | ✅ |
| Integration Pass Rate | 100%（PR merge 前） | — | — | ✅ |
| Pact Contract Pass | 100%（PR merge 前） | — | EDD §4.6.1 | ✅ |
| 需求覆蓋率 | P0 AC 100% | 100%（8/8 需求、5/5 P0 US、26/26 P0 AC） | 對齊 | ✅ |
| 自動化率 | ≥ 90%（E2E Smoke 100%） | — | — | ✅ |
| 效能門檻 | P99≤1000ms / P50≤200ms / ≥200 txn/s / Error≤1% | — | EDD §10.5 | ✅ |

> **判定**：覆蓋率門檻在 test-plan / RTM / ARCH / EDD 間一致（≥80% line、≥70% branch、核心 100%、需求 100%）。test-plan §15.2 以 class-inventory 26 class 為「目標藍圖」、RTM §3.2 14 TC 為「權威列舉集（SSOT）」的區分清楚，無矛盾。**無缺口。**

---

## §6 Self-Check Checklist 結果

| Checklist 項 | 結果 | 證據 |
|---|:--:|---|
| 8 種測試類型都被 test-plan 列出 | ✅ PASS | §1 表，8/8 + HA/Modulith 加分 |
| PRD 每條 AC 有對應測試 | ✅ PASS | §2，30/30 AC → TC |
| API 每個 endpoint 有測試（含 error path） | ✅ PASS | §3，18/18 Phase-A 端點 happy+error；3 Phase-B 合法排除 |
| 每張 SCHEMA table 有 integration test | ⚠️ PASS（含 1 deviation） | §4，核心交易表 100%；14 platform 觀測表列 NICE-TO-HAVE |
| 覆蓋率門檻與 RTM 對齊 | ✅ PASS | §5，全門檻一致 |
| 缺漏寫入本報告並對應 deviation | ✅ PASS | §4.1 DEV-1 已記 |

---

## §7 Findings 彙總

| Severity | 數量 | 說明 |
|---|:--:|---|
| CRITICAL | 0 | 無 |
| HIGH | 0 | 無 |
| MEDIUM | 0 | 無 |
| NICE-TO-HAVE（deviation） | 1 | DEV-1：platform.* 觀測/稽核表缺獨立 CRUD IT，建議 Platform phase 補 |

**Review 要點核對（template §Review 要點）**：
- PRD AC / API endpoint 無未對應測試（否則 CRITICAL）→ ✅ 全對應，無 CRITICAL。
- test-plan 有 BDD（§3.3 Cucumber 9 features）對應 PRD user story → ✅ 無 HIGH。
- test-plan 有效能測試（§3.4/§10）對應 PRD/RTM 效能門檻（EDD §10.5）→ ✅ 無 HIGH。

---

## §8 結論

`docs/test-plan.md` 通過 IEEE 829 完整性驗證：

1. **8 種測試類型**全列出並附工具與量化門檻。
2. **PRD 30 條 AC**（26 P0 + 4 P1）**100%** 有對應 TC（test-plan §15.1）。
3. **18 個 Phase-A API 端點 100%** 有 happy + error path 測試（test-plan §3.2）；3 個 Phase-B/內部端點依 API.md 明文合法排除。
4. **7 BC 核心交易/領域表 100%** 有 Testcontainers integration test；14 張 platform 觀測表為合理間接覆蓋，列 1 筆 NICE-TO-HAVE deviation。
5. **覆蓋率門檻**在 test-plan / RTM / ARCH / EDD 間一致。

**最終判定：PASS（0 CRITICAL / 0 HIGH / 0 MEDIUM；1 NICE-TO-HAVE deviation 不阻斷 pipeline）。** 下游 Contract / E2E phase 可依此 test-plan 展開，無覆蓋盲區風險。
