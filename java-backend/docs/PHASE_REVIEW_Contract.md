# PHASE_REVIEW — Contract

- **Phase**: Contract
- **Generated**: 2026-10-31 (sub-step `contract-phase-review`, `[fallback=self]`)
- **Status**: PASS — `finding_total == 0`
- **Method**: review-only cross-sub-step consistency check (nested spawn unavailable → dispatcher self-review). No regeneration performed.

## 0. Anti-Fake Gate (必做)

`phaseLog.Contract` 實際執行紀錄驗證 — 七個 sub-step 全部 present（非 `completedSubSteps` 字串殘留）：

| sub-step | phaseLog | tddRole | outcome | findingTotal | rounds |
|---|---|---|---|---|---|
| contract-pact-tests | ✅ present | RED | — | 0 | 1 |
| contract-bdd-stepdefs | ✅ present | RED | — | 0 | 2 |
| contract-bdd-client-stepdefs | ✅ present | RED | condition-skip | 0 | 0 |
| contract-controllers | ✅ present | GREEN | — | 0 | 4 |
| contract-ws-handlers | ✅ present | GREEN | condition-skip | 0 | 0 |
| contract-middleware | ✅ present | GREEN | — | 0 | 3 |
| contract-error-codes | ✅ present | GREEN | — | 0 | 1 |

`phase_steps`（pipeline.json Contract，排除自身）= 7；`missing == []` → Anti-Fake Gate PASS，繼續一致性檢查。

## 1. 契約涵蓋度 / 跨 sub-step 一致性驗證

### a. REST Endpoints — 18 == 18（Quantitative Equivalence, Iron Law §4）

API.md §3 列 18 operations（15 unique paths）→ `apps/api/src/main/java/**` 下 8 個 `@RestController` 共 18 個 handler method（實測 `grep -rhoE "@(Get|Post|Put|Delete|Patch)Mapping" = 18`）：

| Controller (O-07) | handlers |
|---|---|
| member/presentation/AuthController | 4 (register/login/logout/me) |
| wallet/presentation/WalletController | 2 (topup/spend) |
| deposit/presentation/DepositController | 2 |
| entitlement/presentation/EntitlementController | 2 |
| record/presentation/RecordController | 2 |
| leaderboard/presentation/LeaderboardController | 2 |
| gamesave/presentation/SaveController | 2 |
| platform/web/HealthController | 2 |

8 controllers × handler = 18 == API.md 18。method/path verbatim，controller 僅委派 per-BC @Transactional app service（無業務邏輯）。**PASS**

### b. Pact Consumer CDC — 6 interactions（3 檔 × 2）

`contract/*PactTest.java` = 3（Member/Deposit/Entitlement → wallet-bc），每檔 2 `@PactTestFor` = 6 interactions；實測 `target/pacts/` 產出 3 pact JSON（member-bc / deposit-bc / entitlement-bc → wallet-bc），consumer CDC GREEN vs Pact mock (V3)。**PASS**

### c. BDD 契約 — 81 scenarios / 489 steps

`src/test/resources/features/*.feature` = 9（== `features/**/*.feature` 上游 9），65 原始 scenario，`RunBddContractIT` 本輪實跑 **81 Scenarios (81 passed) / 489 Steps (489 passed)**（outline 展開）。涵蓋 REST API（真 HTTP + 真 JDBC）、domain event、modulith boundary 三類。0 undefined step。**PASS**

### d. Error Code Registry — 9 == 9（API §5.2）

9 documented codes 全實現：400/401/402/404/409/500 由 composed exception advices（app-root `ApiExceptionHandler` @RestControllerAdvice + member/entitlement per-BC advices, O-10），429 由 `BearerTokenFilter`/`RateLimiter`（O-09），413（servlet-container）/503（graceful shutdown, EDD §3.6.5）於 container/lifecycle 層。`ErrorContractTest` 11 tests 斷言整個 §5.2 registry。零發明碼（無 gold-plating）。**PASS**
> 註：`grep @RestController = 11` = 8 真 controller + 3 `@RestControllerAdvice`（子字串命中），非數量異常。

### e. Middleware / Modulith DAG（O-09, EDD §3.4 HC-5）

`BearerTokenFilter`（app-root）一次解析 opaque Bearer token → `platform.web.CurrentUser`（`CurrentUserArgumentResolver` 注入 controller），退役 per-controller member.api 耦合；`RateLimiter`（EDD §11.3C sliding-window Lua, 5 actions = login/register/wallet/buy/lb, fail-open）429 + Retry-After。`ApplicationModules.of(...).verify()` 於 `bdd/ModulithSteps.java:184`（`modulith_boundaries.feature`，屬 81 綠 scenario 之一）實跑通過 — 零 boundary/cycle 違規，net module graph = DAG。**PASS**

### f. Condition-skip 正當性（2 項，Iron Law §1/§3）

| sub-step | artifactStatus | 驗證 | 判定 |
|---|---|---|---|
| contract-ws-handlers (O-08) | deviation | `grep -icE 'websocket\|wss?://\|stomp' docs/API.md = 0`；openapi.yaml WS=0；`@websocket/@realtime` feature=0；EDD 無 Real-time 章節。API.md L1572「即時功能（本系統目前無）未來以 SSE 補充」 | 合法 condition-skip（0==0，無 WS 契約可對；產 WS handler = 捏造 Iron Law §1） |
| contract-bdd-client-stepdefs (O-48) | deviation | `find features -path '*client*' = 0`；無 `@client` scenario；FRONTEND/VDD/PDD 皆缺；archConfig.client_engine=='none' (api-only) | 合法 condition-skip（無 client artifact 可對；產 client step = 捏造前端） |

兩項皆 zero-on-both-sides，非 §1/§4 遺漏，正當。**PASS**

## 2. 測試閘（empirical，本輪實跑）

| gate | 指令 | 結果 |
|---|---|---|
| 單元 | `mvn -o -pl apps/api test` | **Tests run: 79, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS** |
| 整合 (117 IT) | `mvn -pl apps/api -Dapi.version=1.43 -Dit.test=RunBddContractIT,Jdbc*RepositoryIT,*SchemaIT failsafe`（DOCKER_HOST=npipe, 真 postgres:16 + redis:7.2 Testcontainers） | **Tests run: 117, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS** |

117 IT = `RunBddContractIT` 81 (81 Scenarios/489 Steps passed) + 7 Jdbc*RepositoryIT (20) + 4 SchemaIT (16) = 36 persist IT。全部對真實 ephemeral postgres:16（migrations applied）+ redis:7.2 執行。**79 unit + 117 IT 全綠，無 regression。**

## 3. Deviations 審查

Contract phase deviations = 30，全部 `severity: NICE-TO-HAVE`，`BLOCKING = 0`，mark 全為 `DEFAULT-APPLIED`（Iron Law §2 doc-silent + 套預設）。無任何 §1（文件明說沒做）或 §4（數量不對）被降級為 deviation。代表性項目：

- `contract-pact-tests / output_path` — 泛用 `tests/contract/` 落在 `src/test/java/.../contract/`（Maven java 需 src/test/java 才能編譯/Surefire-Failsafe 執行）。
- `contract-pact-tests / provider_verification_deferred` — 僅 pact.consumer:junit5 在 .m2（provider:junit5 缺, offline）；consumer CDC 重現全 6 interaction，provider 驗證隨 CICD。
- `contract-ws-handlers` / `contract-bdd-client-stepdefs` — 上游無 WS / client 契約的合法 condition-skip（見 §1.f）。

全部屬 Iron Law §2 合法場景，**無 Iron Law 違規**。

## 4. 結論

| 檢查項 | 結果 |
|---|---|
| Anti-Fake Gate（7/7 phaseLog present） | PASS |
| REST Endpoints 18 == 18 | PASS |
| Pact 6 interactions (3×2) | PASS |
| BDD 81 scenarios / 489 steps | PASS |
| Error codes 9 == 9 (§5.2) | PASS |
| Middleware + Modulith verify() DAG | PASS |
| condition-skip 正當 (WS / client BDD) | PASS |
| 單元 79 green | PASS |
| 整合 117 IT green (real postgres:16 + redis:7.2) | PASS |
| deviations 合理 (30 NICE-TO-HAVE, 0 BLOCKING) | PASS |
| Iron Law 違規 | 無 |

**finding_total = 0 → Contract phase 標為 completedPhase。** 至此 6 個 phase（Recon / Foundation / Domain / Orchestrate / Persist / Contract）全部完成。
