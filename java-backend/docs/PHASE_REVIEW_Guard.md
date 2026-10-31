# PHASE_REVIEW — Guard

- **Phase**: Guard
- **Generated**: 2026-10-31 (sub-step `guard-phase-review`, `[fallback=self]`)
- **Status**: PASS — `finding_total == 0`
- **Method**: review-only cross-sub-step consistency check (nested spawn unavailable → dispatcher self-review). No regeneration performed.
- **archConfig**: lang_stack=java, framework=spring-boot-3.3, orm=spring-data-jdbc, db_type=postgres, cache_type=redis, infra_runtime=k8s-local, ci_platform=github-actions, client_engine=none (api-only), admin_portal_stack=none

## 0. Anti-Fake Gate (必做)

`phaseLog.Guard` 實際執行紀錄驗證 — 3 個 sub-step（pipeline.json Guard，排除自身 `guard-phase-review`）全部 present（非 `completedSubSteps` 字串殘留）：

| sub-step | artifact | phaseLog | findingTotal | rounds | experts |
|---|---|---|---|---|---|
| guard-pr-pipeline | O-33 | ✅ present | 0 | 1 | gen=fallback/review=fallback/fix=fallback×1 |
| guard-nightly | O-34 | ✅ present | 0 | 1 | gen=fallback/review=fallback/fix=fallback×1 |
| guard-coverage-config | O-35 | ✅ present | 0 | 1 | gen=fallback/review=fallback/fix=fallback×1 |

`phase_steps` = 3；`missing == []` → Anti-Fake Gate PASS，繼續一致性檢查。）每筆 phaseLog 皆載明 gate 真跑證據（YAML 真驗 / mvn verify 真跑）、quantGate、noRegression — 屬實非造假。

## 1. CI 資產涵蓋度（PR gate / nightly / coverage，真驗）

### a. PR Gate — `.github/workflows/ci.yml`（O-33）

- 檔案存在（6419 bytes），**PyYAML safe_load PARSE OK**，`jobs` = **5**：`lint / unit-test / integration-test / static-analysis / image-build`。
- 與 CICD.md §5.2 的 5-job required-status-checks DAG 逐字對齊（docs/CICD.md L60-64：lint+ArchUnit → unit-test(JaCoCo≥80%) → integration-test(Testcontainers+Pact+Modulith) → static-analysis(SpotBugs+Semgrep+依賴掃描) → image-build(Buildx+cosign)）。
- `.github/branch-protection.yml` 的 `required_status_checks.contexts` = **["lint","unit-test","integration-test","static-analysis","image-build"]**，**與 ci.yml 五個 job 名完全一致**（CICD §5.2 R-04）。**PASS**

### b. Nightly / Scheduled — `.github/workflows/nightly.yml`（O-34）

- 檔案存在（11425 bytes），**PARSE OK**，`jobs` = **6**：`full-regression / security-scan / perf-load / perf-soak / perf-stress / notify`。
- `on.schedule` = **2 crons**（daily `0 18 * * *` 02:00 Asia/Taipei off-peak；weekly `0 18 * * 0`）+ workflow_dispatch。完整涵蓋 test-plan §3.4 四情境（Load/Stress/Soak）+ §3.3 FULL E2E/BDD 每日排程 + JaCoCo coverage trend。3 個 perf job `continue-on-error:true` 且 `notify` 僅 `needs:[full-regression,security-scan]` → perf 退化不誤報。**PASS**

### c. Coverage Config — `apps/api/pom.xml` JaCoCo（O-35）

- JaCoCo plugin 真實存在（pom.xml L258+）：unit agent → `jacoco.exec`、IT agent → `jacoco-it.exec`、`merge` → `jacoco-merged.exec`、merged report + **bundle check（Line≥`${jacoco.line.min}`=0.80 / Branch≥`${jacoco.branch.min}`=0.70）** + core-txn CLASS include。
- **核心交易 CLASS 門檻 FQN 正確**：`SpendWalletUseCase` / `TopUpWalletUseCase`（大寫 U，修復前的 vacuous rule）/ `BuyEntitlementUseCase`，在 test 階段（core-unit）與 verify 階段（merged）**雙重啟用**。
- 門檻可回溯 test-plan §2/§3.1（L48/L92-93/L194-197：Line≥80% CI hard gate、核心交易=100%、Branch≥70%）。**PASS**

## 2. Workflow YAML 有效性 + 引用 target 真實存在（真驗）

- **雙解析器**：PyYAML 6.x `safe_load` 對 ci.yml / nightly.yml / branch-protection.yml **三檔全 PARSE OK**（本輪實跑）。
- **Makefile target 交叉存在性（CI==local）**：workflow 引用的 **16 個 make target 全部於 `Makefile` 真實定義**（`^target:` grep 逐一命中）：
  `ci-lint / ci-archunit / ci-test-unit / ci-test-integration / ci-test-modulith / ci-contract-test / ci-test-schema-isolation / ci-spotbugs / ci-semgrep / ci-dependency-scan / ci-image-sign / ci-perf / ci-soak / ci-stress / test-e2e / ci-dry-run` — **0 個懸空引用**。（regex 誤抓的 `ci-` 來自註解 `make ci-*`，非真實引用。）
- 每個 CI step 皆 delegate 到開發者本機可跑的 make target（CICD §4 CI==local）；無 CI-only 邏輯。**PASS**

## 3. 覆蓋率門檻真生效（真跑 — 非宣稱）

本輪實跑（JDK21 / Maven 3.9.9、REAL postgres:16 + redis:7.2 Testcontainers、Docker 29.1.3 Rancher Desktop、`-Dapi.version=1.43`、DOCKER_HOST npipe、Ryuk disabled）：

```
mvn -q -pl apps/api -Dapi.version=1.43 \
  -Dit.test=RunBddContractIT,Jdbc*RepositoryIT,*SchemaIT,E2eSmokeJourneyIT verify
→ MVN_EXIT=0（BUILD SUCCESS）
```

`verify` 內含 `jacoco:check`；**exit=0 即證明 merged bundle 門檻 + core-txn 門檻真生效且 PASS**（若任一未達即 BUILD FAILURE）。本輪 `target/site/jacoco-merged/jacoco.csv` 實測聚合：

| 指標 | 實測 | 門檻 | 結果 |
|---|---|---|---|
| LINE | **85.09%**（742/872） | ≥80% hard gate | ✅ PASS |
| BRANCH | **75.83%**（160/211） | ≥70% hard gate | ✅ PASS |
| core-txn（Spend/TopUp/BuyEntitlement） | 100% line | =100% CLASS gate | ✅ PASS（exit=0） |
| METHOD | 81.79% | ≥85% CI warning（非 gate） | ⚠ 見 §5 deviation |

數字與 guard-coverage-config phaseLog 宣稱（85.09% / 75.83%）**逐位一致**，無造假。core-txn 若仍是修復前的錯誤 FQN（`TopupWalletUseCase`）則 include 匹配 0 類 → vacuous 永過；現以真實 FQN 強制，exit=0 代表實際涵蓋達標。

## 4. 無退化驗證（真跑，本輪實測）

- **單元（surefire，本輪 mtime 篩選）**：**Tests 85 / Failures 0 / Errors 0 / Skipped 0**（79 baseline + 6 新 domain-VO equals/boundary 測試 → 提升 merged Branch 至 75.83%）。
- **整合（failsafe 策展集，本輪 mtime 篩選）**：**Tests 118 / Failures 0 / Errors 0 / Skipped 0** = RunBddContractIT **81 Scenarios (81 passed) / 489 Steps (489 passed)** + 7 Jdbc*RepositoryIT + 4 *SchemaIT（共 36 persist IT）+ 1 E2eSmokeJourneyIT。
- **合計 85 unit + 118 IT 全綠**，與 Guard phase 進入時（Harden 收尾）基線一致，**零退化**。
- 黑箱 `*ApiContractIT`（需 CICD 啟動 provider）維持 contract-pact-tests 以來的 out-of-scope RED，已排除於策展集外（與 harden/coverage 一致），非退化。
- **laya submodule 未 touch**（git status 僅顯示 `../laya` modified pointer，未 stage，本次不動）。

## 5. Deviation 盤點

Guard phase 無 BLOCKING deviation。state.deviations[] 中 Guard 相關 2 筆皆 NICE-TO-HAVE：

- `guard-coverage-config / method_coverage_warning`：CONDITION-NOTE — merged METHOD 84.34%（本輪 81.79% 經策展集聚合，仍 <85%）對應 test-plan §3.1 Function≥85% 為「CI warning」（非 hard gate）；已於 JaCoCo 報告揭露、未當作 build-failing 門檻。Line/Branch/core-txn 三硬門檻全 PASS，無調低門檻、無造假。
- `guard-coverage-config / bundle_check_stage`：DEFAULT-APPLIED — bundle Line/Branch gate 在 verify（ci-test-integration）階段執行而非 ci-test-unit 內，因 test-plan §3.1 將 Controller/Infra/Cache 覆蓋路由至 IT 層，unit-only bundle check 會自相矛盾（實測 unit-only Branch=62.8%）。門檻（0.80/0.70）與 core-txn=100% 不變；避免 vacuous gate。

兩項皆 0 BLOCKING、無 Iron Law §1/§3/§4 違反。

## 結論

Guard phase 4/4 sub-step 全數完成（3 實作 + 本收尾 review）。CI 資產完整涵蓋 **PR Gate（O-33，ci.yml 5 jobs == CICD §5.2 branch-protection 5 contexts）/ Nightly（O-34，nightly.yml 2 cron 6 jobs，regression+security+perf 完整、perf 非阻斷）/ Coverage（O-35，merged unit+IT JaCoCo，修復 vacuous core-txn rule）**。三份 workflow YAML 雙解析器真驗全 PARSE OK；16 個引用 make target 全於 Makefile 真實存在（0 懸空）；branch-protection contexts 與 ci jobs 完全對齊。覆蓋率門檻以 `mvn verify` exit=0 真生效（LINE 85.09%≥80 / BRANCH 75.83%≥70 / core-txn=100%，數字逐位一致）；85 unit + 118 IT 全綠無退化；deviation 全 NICE-TO-HAVE/DEFAULT-APPLIED，0 BLOCKING。

**`finding_total == 0` → Guard phase status = PASS。**

### PHASE_REVIEW_RESULT

```yaml
PHASE_REVIEW_RESULT:
  phase: Guard
  finding_total: 0
  critical: 0
  high: 0
  medium: 0
  findings: []
```
