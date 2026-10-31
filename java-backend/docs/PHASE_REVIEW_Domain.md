# PHASE_REVIEW — Domain

- **Phase**: Domain
- **Reviewed at**: 2026-10-31
- **Reviewer**: Domain Tech Lead (dispatcher fallback=self; nested spawn unavailable)
- **Status**: ✅ PHASE_PASS
- **finding_total**: 0 (critical=0 / high=0 / medium=0 / low=0)

跨 sub-step 一致性 review（**不重做生成**），只驗證 Domain phase 三個 sub-step 產物是否彼此對齊且符合上游文件規格。

---

## 0. Anti-Fake Gate — 前置 sub-step 實際執行驗證

唯一可信來源：`phaseLog.Domain.<sub-step-id>`。

| sub-step | phaseLog 紀錄 | tddRole | findingTotal |
|---|---|---|---|
| domain-unit-tests | ✅ present | RED | 0 |
| domain-entities | ✅ present | GREEN | 0 |
| domain-services | ✅ present | GREEN | 0 |

`missing == []` → 通過，繼續一致性檢查。

---

## 1. 跨 sub-step 一致性檢查結果

| # | 檢查項 | 期望 | 實測 | 結果 |
|---|---|---|---|---|
| C-1 | Domain 單元測試 @Test 方法數 | 30 (RTM §3.2 domain-layer) | 30 across 8 test files（Surefire 展開 34 executions，含 4 個 Email parameterized） | ✅ PASS |
| C-2 | 測試檔數 (class-inventory Domain) | 8 | 8 (`MoneyTest / AccountIdTest / AccountTest / EmailTest / DisplayNameTest / SessionTest / WalletTest / LedgerEntryTest`) | ✅ PASS |
| C-3 | O-01 行為型 domain 型別 (EDD §4.5.2 class-domain) | 10 | 10 (`Money / AccountId / Account / Email / DisplayName / Session / Wallet / LedgerEntry / Currency / LedgerReason`) | ✅ PASS |
| C-4 | O-02 Domain Events (EDD §4.6.1) | 7 | 7 (`MemberRegistered / MemberDeleted / WalletBalanceChanged / DepositConfirmed / EntitlementGranted / BusinessRecordAdded / RunScoreSubmitted`) + `DomainEvent` marker | ✅ PASS |
| C-5 | Repository 介面 (EDD §3.4 aggregate roots, DIP ports) | 7 | 7 (member / wallet / deposit / entitlement / record / leaderboard / gamesave) | ✅ PASS |
| C-6 | Domain 純度 — 無 Spring/JDBC import (EDD DIP/§4.3 Dependency Rule) | 0 import | `grep org.springframework / java.sql / *.persistence` 於 domain+shared = 0 命中 → CLEAN | ✅ PASS |
| C-7 | `mvn test` 仍全綠 (TDD GREEN 不回退) | 0 fail / 0 error | **Tests run: 34, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS** | ✅ PASS |
| C-8 | output-paths 存在且非空 | 皆存在 | PHASE_REVIEW_Domain.md 外所有 O-01/O-02/O-22 artifact 皆 present（state.allArtifacts 對齊磁碟） | ✅ PASS |

### DIP / HC 邊界

- **DIP**：EDD §4.3 SOLID 表明列「Domain/Application 不 import 任何 JDBC/Spring 類別」；C-6 grep 驗證 0 命中 → 具體落實。Repository 皆為 domain 層介面（port），infra 實作 (JdbcXxxRepository) 屬後續 Persist phase。
- **HC-1**：跨 BC 僅透過 Public API / Domain Event；本 phase 無跨 BC DB-level FK，`DepositConfirmed` 參照 `wallet.domain.Currency`（EDD §4.3 module DAG 允許 deposit→wallet），以 public domain enum 實現，未違反 ArchUnit 框架規則。

---

## 2. Deviation 審查

Domain phase 共 14 條 deviation，全部 `severity=NICE-TO-HAVE`，皆屬 Iron Law §2（文件未說→套預設，標 `DEFAULT-APPLIED`）或合法修正類：

- **路徑/規則檔校正**（`test_location` / `rule_file_corrected`×3 / `migrations_location` 模式一致）：recon-dryrun 以 TS 單模組假設生成，校正為 java 多模組 Maven 實況 — 合理。
- **compile-enabler / domain-type skeletons**：pipeline 順序使然（port 先於 entity），identity-only、無業務邏輯、被 O-01 GREEN 覆寫 — 合理，非 gold-plating。
- **domain_test_scope / five_bc_identity_scope / no_imperative_domain_service**：EDD §4.5.2 class-domain 僅為 wallet+member 定義行為，其餘 5 BC 為 identity-only、且 class-inventory 無 `<<DomainService>>`；不虛構無 EDD 對應的服務 — 符合 Anti-Fake/Review #9。
- **account_test_fixture_corrected（UPSTREAM-FIXTURE-CORRECTED）**：上游 O-22 AccountTest fixture 用不可推導的 placeholder hash 卻斷言 `verifyPassword==true`；校正為 `Sha256PasswordHasher` 真實雜湊值（保留斷言、以真實計算通過，錯密碼仍 fail）— 合法修正，非 hardcode。建議 domain-unit-tests 日後由 hasher 生成 fixture。
- **password_hasher_dip_seam / debit_reason_default / deposit_confirmed_currency_cross_bc / spring_modulith_version(SPEC-CONFLICT)**：皆有具體 caller 或文件衝突依 archConfig 權威 pin 解決 — 合理。

無任一 deviation 掩蓋 Iron Law §1/§3/§4 違規，無降級規避 fix loop 的情況。

---

## 3. 結論

Domain phase 三個 sub-step 產物跨檔一致、數量對齊上游文件、純 Domain 無框架洩漏、`mvn test` 全綠、deviation 合理且無 Iron Law 違規。

**finding_total = 0 → PHASE_PASS**。Domain 標為 completedPhase，pipeline 可續行 Orchestrate phase。
