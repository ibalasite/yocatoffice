# PHASE_REVIEW — Persist

- **Phase**: Persist
- **Generated**: 2026-10-31 (sub-step `persist-phase-review`, `[fallback=self]`)
- **Status**: PASS — `finding_total == 0`
- **Method**: review-only cross-sub-step consistency check (nested spawn unavailable → dispatcher self-review). No regeneration performed.

## 0. Anti-Fake Gate (必做)

`phaseLog.Persist` 實際執行紀錄驗證 — 四個 sub-step 全部 present（非 `completedSubSteps` 字串殘留）：

| sub-step | phaseLog | tddRole | findingTotal | rounds |
|---|---|---|---|---|
| persist-integration-tests | ✅ present | RED | 0 | 2 |
| persist-repo-impls | ✅ present | GREEN | 0 | 1 |
| persist-adapters | ✅ present | GREEN | 0 | 1 |
| persist-cache-config | ✅ present | — | 0 | 1 |

`missing == []` → Anti-Fake Gate PASS，繼續一致性檢查。

## 1. 跨 sub-step 一致性驗證

### a. 7 Repository port ↔ 7 Infrastructure adapter（元件數對齊）

EDD §3.4 Aggregate Roots = 7 → `*/domain/*Repository.java` = 7 → `*/infrastructure/Jdbc*Repository.java` = 7，一一對應：

| BC | Port (O-03, domain) | Adapter (O-04, infrastructure) |
|---|---|---|
| member | MemberRepository | JdbcMemberRepository |
| wallet | WalletRepository | JdbcWalletRepository |
| deposit | DepositPackRepository | JdbcDepositPackRepository |
| entitlement | EntitlementRepository | JdbcEntitlementRepository |
| record | BusinessRecordRepository | JdbcBusinessRecordRepository |
| leaderboard | RunScoreRepository | JdbcRunScoreRepository |
| game-save | OfficeSaveRepository | JdbcOfficeSaveRepository |

每個 adapter public-method set == port method set（無 gold-plating）— per persist-repo-impls quantGate。**PASS**

### b. SELECT FOR UPDATE（EDD §4.5.4.1 悲觀鎖）

`JdbcWalletRepository.findForUpdate()` 發 `SELECT account_id, cash, gems FROM wallet.wallets WHERE account_id = ? FOR UPDATE`，取 row-level 悲觀鎖；IT `JdbcWalletRepositoryIT` CONS-01 以 10 併發 debit 序列化至 0 驗證。**PASS**

### c. Transactional Outbox（EDD §4.6）

`platform/ModulithEventPublisher`（@Component, implements shared/DomainEventPublisher port）包 `ApplicationEventPublisher`，事件於**同一 DB 交易**寫入 `modulith.event_publication`；migration `V009__create_modulith_outbox.sql` + `CREATE SCHEMA modulith`。`OutboxSchemaIT` (3 tests) 驗 outbox 表結構。**PASS**

### d. schema-per-BC（V001）

`CREATE SCHEMA IF NOT EXISTS` × 9：member / wallet / deposit / entitlement / record / leaderboard / game_save（7 BC）+ modulith + platform（2 橫切）。adapter 查詢全以 `<schema>.<table>` 限定（如 `wallet.wallets`）。**PASS**

### e. HC 邊界 / 強一致性（無 Redis gold-plating）

O-15 cache-config = 10 artifacts：平台層 7（RedisConfig / CacheNamespace / CacheTtl / CacheMetrics / CacheGuard / RateLimitAction / RateLimiter）+ 每 BC 隔離 2（member SessionCache / leaderboard LeaderboardCache）+ `rate_limit_sliding_window.lua`。EDD §21.3 key namespace = 3 families（member/leaderboard/rate）== CacheNamespace builder families=3；§11.3C rate-limit actions=5。wallet/deposit/entitlement/record/game-save **無 Redis key**（DB 強一致），故無 cache component — 不 gold-plating。CacheGuard 經 Resilience4j 'redis' circuit breaker fail-open（§8.3），cache 失敗不轉 5xx。**PASS**

### f. 其他適配器（O-06, EDD §4.5.2 Infrastructure <<Adapter>>）

ScryptPasswordEncoder（implements member/domain PasswordHasher, BouncyCastle scrypt N=2^14/r=8/p=1, constant-time compare）+ ModulithEventPublisher + SessionTokenGenerator（opaque 32B-hex SecureRandom）。BearerTokenFilter 合理延後至 contract-middleware（O-09，依賴未建的 MemberService）。**PASS**

## 2. 測試閘（empirical，本輪實跑）

| gate | 指令 | 結果 |
|---|---|---|
| 單元 | `mvn -o -pl apps/api test` | **Tests run: 62, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS** |
| 整合 (36 IT) | `mvn -pl apps/api -Dtest='*IT' -DargLine='-Dapi.version=1.43' test`（DOCKER_HOST=npipe, 真 postgres:16 Testcontainers） | **Tests run: 36, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS** |

36 IT = 7 Jdbc*RepositoryIT (20) + 4 SchemaIT (16)，全部對真實 ephemeral postgres:16（migrations V001–V012 applied）執行。**全綠，無 regression。**

## 3. Deviations 審查

Persist phase deviations = 24，全部 `severity: NICE-TO-HAVE`，`BLOCKING = 0`。mark 分佈合法：DEFAULT-APPLIED（§2 doc-silent + 套預設）/ UPSTREAM-ENTITY-ENRICHED / UPSTREAM-MIGRATION-CORRECTED。無任何 §1（文件明說沒做）或 §4（數量不對）被降級為 deviation 的情況。代表性項目：

- `testcontainers_api_version_env_requirement` — docker-java 3.3.6 預設 API 1.32 被 Rancher dockerd（min 1.41）拒；`-Dapi.version=1.43` 修復（已於本輪實跑驗證有效）。
- `bearer_token_filter_deferred_to_contract_middleware` — 依賴未建的 MemberService，合理延後至 O-09。
- `cache_callers_deferred_to_contract_phase` — cache 基建就緒，呼叫方（controller/service）屬 Contract phase。

全部屬 Iron Law §2 合法場景，**無 Iron Law 違規**。

## 4. 結論

| 檢查項 | 結果 |
|---|---|
| Anti-Fake Gate（4/4 phaseLog present） | PASS |
| 7 port ↔ 7 adapter | PASS |
| SELECT FOR UPDATE | PASS |
| Transactional Outbox | PASS |
| schema-per-BC (9 schemas) | PASS |
| HC 邊界 / 無 gold-plating | PASS |
| 單元 62 green | PASS |
| 整合 36 IT green (real postgres:16) | PASS |
| deviations 合理 (24 NICE-TO-HAVE, 0 BLOCKING) | PASS |
| Iron Law 違規 | 無 |

**finding_total = 0 → Persist phase 標為 completedPhase。**
