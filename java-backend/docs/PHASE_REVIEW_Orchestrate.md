# PHASE_REVIEW — Orchestrate

- **Phase**: Orchestrate
- **Reviewed at**: 2026-10-31
- **Reviewer**: Orchestrate Tech Lead (dispatcher fallback=self; nested spawn unavailable)
- **Status**: ✅ PHASE_PASS
- **finding_total**: 0 (critical=0 / high=0 / medium=0 / low=0)

跨 sub-step 一致性 review（**不重做生成**），只驗證 Orchestrate phase 兩個 sub-step 產物是否彼此對齊且符合上游文件規格。

---

## 0. Anti-Fake Gate — 前置 sub-step 實際執行驗證

唯一可信來源：`phaseLog.Orchestrate.<sub-step-id>`（非 `completedSubSteps`）。

| sub-step | phaseLog 紀錄 | tddRole | findingTotal |
|---|---|---|---|
| orchestrate-app-tests | ✅ present | RED | 0 |
| orchestrate-usecases | ✅ present | GREEN | 0 |

`missing == []` → 通過，繼續一致性檢查。

---

## 1. 跨 sub-step 一致性檢查結果

| # | 檢查項 | 期望 | 實測 | 結果 |
|---|---|---|---|---|
| C-1 | Application 單元測試檔數 (class-inventory Application-layer) | 5 | 5 (`SpendWalletUseCaseTest / TopUpWalletUseCaseTest / BuyEntitlementUseCaseTest / RegisterMemberUseCaseTest / WalletServiceTest`) | ✅ PASS |
| C-2 | Application 層 @Test 方法數 (RTM §3.2) | 20 (含 1 parameterized bounds) | 20 annotations across 5 files（Surefire 展開 21 executions，TopUp parameterized → 5） | ✅ PASS |
| C-3 | O-05 use case / ApplicationService 實作 | 5 | 5 (`SpendWalletUseCase / TopUpWalletUseCase / BuyEntitlementUseCase / RegisterMemberUseCase` UseCases + `WalletService` ApplicationService) | ✅ PASS |
| C-4 | RED→GREEN 轉換完成（無殘留 stub） | 0 `UnsupportedOperationException` | grep 於 5 impl = 0 命中；orchestrate-app-tests 的 RED placeholder 全部 GREEN | ✅ PASS |
| C-5 | DIP — Application 只依 port 介面，0 框架/infra import (EDD §4.3) | 0 import | `grep ^import (org.springframework\|jakarta\|java.sql\|org.hibernate\|javax.persistence\|com.zaxxer)` 於 5 impl = 0 命中 → CLEAN | ✅ PASS |
| C-6 | CQRS 分離 (EDD §4.5.2) | Query 無副作用 | `WalletService.findWallet` 為純 Query（無 save / 無 appendLedger / 無 publish）；`topUp` / `debit` 為 Command（mutate + ledger + event） | ✅ PASS |
| C-7 | 跨 BC 僅透過 Public API (EDD §4.3 module DAG) | entitlement→wallet 走 Public 介面 | `BuyEntitlementUseCase` 經注入的 `WalletService`（wallet BC Public NamedInterface）debit，未觸 wallet 內部 repo | ✅ PASS |
| C-8 | `mvn -pl apps/api test` 仍全綠（TDD GREEN 不回退 + 無 domain regression） | 55 / 0 / 0 / 0 | **Tests run: 55, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS**（34 domain baseline + 21 Application executions） | ✅ PASS |
| C-9 | output-paths 存在且非空 | 皆存在 | 本報告外所有 O-05 / O-22(app) artifact 皆 present（state.allArtifacts 對齊磁碟） | ✅ PASS |

### DIP / CQRS / 跨 BC 邊界

- **DIP**：5 個 Application 實作僅依賴 domain port（`WalletRepository` / `EntitlementRepository` / `MemberRepository` / `PasswordHasher` / `DomainEventPublisher`）與 wallet BC Public 介面 `WalletService`，建構子注入、無任何 Spring/JDBC/Jakarta 具體型別。C-5 grep 0 命中 → 具體落實。Repository 實作 (JdbcXxxRepository) 屬後續 Persist phase。
- **CQRS**：`WalletService` 明確拆分 Command（`topUp` / `debit`：mutate balance + appendLedger + publish `WalletBalanceChanged`）與 Query（`findWallet`：唯讀，無 save / ledger / event）。
- **交易語意**：`SpendWalletUseCase` / `WalletService.debit` 的金額守衛（→400）在 `findForUpdate` 行鎖**之前**執行；domain 402 守衛在 `Wallet.debit` 內，失敗不落寫、不發事件 — 對齊 orchestrate-app-tests 斷言（金額越界時 `findForUpdate` 不被呼叫）。
- **跨 BC**：entitlement→wallet 僅經 `WalletService` Public 介面（EDD §4.3 DAG 允許）；member `RegisterMemberUseCase` 於持久化**後**發 `MemberRegistered`，供 wallet BC listener 開通初始錢包（EDD §3.4 / §7.1）。

---

## 2. Deviation 審查

Orchestrate phase 新增 7 條 deviation（orchestrate-app-tests 5 + orchestrate-usecases 3，其中 1 條重疊計），全部 `severity=NICE-TO-HAVE`，皆屬 Iron Law §2（文件未說→套預設，標 `DEFAULT-APPLIED`）或合法修正類：

- **app_test_scope_class_inventory_ssot / test_location_multimodule / rule 校正**：以 class-inventory.md（SSOT）+ RTM §3.2 枚舉 Application-layer 測試集為 5；dispatcher prompt 提及的 MemberService/LeaderboardService 為示例非權威 scope override；多模組路徑校正同 domain 前例 — 合理，不虛構 Application 單測屬 gold-plating。
- **mockito_over_handwritten_stub**：test-plan §3.1（required doc）明定 Application 層「JUnit 5 + Mockito, mock all Interface」；Iron Law §1/§3 required-doc 優先於 template 的 TS 專屬 `no jest.mock` 字句；Domain 層測試仍 Mockito-free — 合理。
- **red_compile_enabler_app_skeletons / passwordhasher_as_register_port**：pipeline 順序使然（RED 測試先於 GREEN 實作），compile-enabler 簽章 grounded 於 EDD §4.5.2 class-application，body throw 保持 RED，已被 orchestrate-usecases GREEN 覆寫；`PasswordHasher` 複用 domain 既有 port（同 encode/matches 合約，EDD §4.5.2 稱 PasswordEncoder）— 合理。
- **entitlement_grant_identity_only**：承 domain `five_bc_identity_scope`（EDD §4.5.2 class-domain 未為 entitlement 定義欄位/行為）；測試本身以 `new Entitlement()` 構造並只驗 `save(any)` + 錢包餘額；完整欄位填充留待 Persist phase — 合理，避免跨 sub-step 改 O-01（Iron Law §6）。
- **ledger_reason_assigned_at_application_layer / amount_bounds_and_password_min_named_constants**：EDD §4.7 Alg 1 明定 reason 於 application 層指派；金額界 [1,10_000_000]、PASSWORD_MIN=6 皆為引用 CONSTANTS §8 / PRD §5.1 AC-5 的具名常數（非 magic number），且必須在行鎖前守衛 — 合理。

無任一 deviation 掩蓋 Iron Law §1/§3/§4 違規，無降級規避 fix loop 的情況。

---

## 3. 結論

Orchestrate phase 兩個 sub-step 產物跨檔一致：5 use case/ApplicationService 對齊 class-inventory 與 EDD §4.5.4 use-case 編排、20 Application @Test RED→GREEN 全數轉綠、DIP 乾淨（0 框架 import，純 port 注入）、CQRS 正確分離、跨 BC 僅走 Public 介面、`mvn -pl apps/api test` 55/0/0/0 BUILD SUCCESS 無 domain regression、deviation 全 NICE-TO-HAVE 且合理、無 Iron Law 違規。

**finding_total = 0 → PHASE_PASS**。Orchestrate 標為 completedPhase，pipeline 可續行 Persist phase。
