# PHASE_REVIEW — Harden

- **Phase**: Harden
- **Generated**: 2026-10-31 (sub-step `harden-phase-review`, `[fallback=self]`)
- **Status**: PASS — `finding_total == 0`
- **Method**: review-only cross-sub-step consistency check (nested spawn unavailable → dispatcher self-review). No regeneration performed.
- **archConfig**: lang_stack=java, framework=spring-boot-3.3, orm=spring-data-jdbc, db_type=postgres, cache_type=redis, infra_runtime=k8s-local, ci_platform=github-actions, client_engine=none (api-only), admin_portal_stack=none, storage_type='' (none)

## 0. Anti-Fake Gate (必做)

`phaseLog.Harden` 實際執行紀錄驗證 — 6 個 sub-step（pipeline.json Harden，排除自身 `harden-phase-review`）全部 present（非 `completedSubSteps` 字串殘留）：

| sub-step | artifact | phaseLog | outcome | findingTotal | rounds |
|---|---|---|---|---|---|
| harden-stubs | O-20 | ✅ present | condition-skip | 0 | 1 |
| harden-e2e-smoke | O-26 | ✅ present | exec (real-run) | 0 | 1 |
| harden-perf | O-27 | ✅ present | exec (real-run) | 0 | 1 |
| harden-k8s-smoke | O-38 | ✅ present | exec (real deploy) | 0 | 1 |
| harden-client-integration | O-39 | ✅ present | condition-skip | 0 | 0 |
| harden-runbook | O-41 | ✅ present | — | 0 | 1 |

`phase_steps` = 6；`missing == []` → Anti-Fake Gate PASS，繼續一致性檢查。）

## 1. 跨 sub-step 一致性驗證（真跑）

### a. 硬化資產涵蓋度（e2e / perf / k8s-smoke / runbook 全到位）

本輪實測磁碟存在性 + 語法驗證：

- **E2E（O-26）**：`apps/api/src/test/java/com/sayyogames/yocat/e2e/E2eSmokeJourneyIT.java`（in-process 全棧 @SpringBootTest(RANDOM_PORT) real-run proof）+ `tests/e2e/{smoke.spec.ts, playwright.config.ts, package.json, README.md}`（部署後 ingress/NodePort smoke）。同一條 12 步旅程雙 runner。**PASS**
- **Perf（O-27）**：`tests/perf/` = 8 個 `*.js`（smoke/load/stress/soak + lib/{config,http,workload,summary}），`node --check` 全 OK。SLO 逐字抄自 test-plan §3.4/§10.2 + EDD §10.5。Makefile test-perf/ci-perf 已對齊 `tests/perf/load-test.js`。**PASS**
- **K8s-smoke（O-38）**：`scripts/k8s-smoke.sh`（authoritative runner）+ `tests/smoke/k8s-deployment.test.ts`（CI wrapper）+ `docs/K8S_SMOKE_REPORT.md`（real-run record）。`bash -n` OK。**PASS**
- **Runbook（O-41）**：`scripts/runbook/{health-check,deploy-rollback,disaster-recovery,daily-inspection}.sh`（4 隻，`bash -n` 全 OK，`set -euo pipefail`）+ `docs/RUNBOOK_GUIDE.md`。4 腳本 1:1 對應 RUNBOOK 章節。**PASS**

### b. artifact_ids 全對齊（Iron Law §4 Quantitative Equivalence）

`artifactStatus`：O-26 / O-27 / O-38 / O-41 = **present**；O-20 / O-39 = **deviation**（condition-skip，見 §2）。與 6 sub-step 的 outcome 完全一致，無漏標、無造假。

- Flyway 鏈 `migrations/V*.sql` = **V001–V013 連續 13 檔**（無跳號、無重號）；`CREATE TABLE` literal 計數 = **40**（SCHEMA §1.2 quant-gate 不變 — harden-k8s-smoke 的 V010 `IF NOT EXISTS` 修復刻意保留 literal）。
- perf `*.js` = 8；e2e E2E test = 1；runbook `.sh` = 4；皆與 phaseLog 宣稱數量一致。

### c. 真部署證據（harden-k8s-smoke，本 phase 最硬核的 real-run）

`docs/K8S_SMOKE_REPORT.md` + phaseLog 記錄：RC=0 @ 2026-10-31T08:26:44+0800，context rancher-desktop / node aibala v1.25.16+k3s4 / runtime docker://29.1.3。`docker build` → `kubectl apply -k overlays/local` → Flyway Job「Successfully applied 13 migrations ... now at v013」→ rollout 2/2 replicas → /health/live 200 `{"status":"UP"}` + /health/ready 200 `{"status":"UP","db":"UP","redis":"UP"}` → API smoke register→topup g_big 700→spend 10→690→overdraw 1,000,000→**HTTP 402「餘額不足」(balance unchanged)** → HA pod-kill：存活 replica 經 Service LB 持續服務 + deployment 自癒 → teardown（namespace 刪除 + DROP 9 yocat schemas，membership public 8 表完好）。此為 pipeline 首個 Flyway-driven deploy，揭露並修復真實缺陷（V010 schema_migrations 與 Flyway history 42P07 衝突）。**PASS**

### d. 無退化 — 跨 phase 引用鏈完整（真跑，見 §3）

Harden 測試資產引用 Platform/Contract/Persist 既有產物（E2eSmokeJourneyIT 驅動 contract controllers；k8s-smoke apply Platform 的 k8s overlays + Flyway Job；perf 打 API §3 endpoints）。全部 import/路徑真實存在 — 由 118 IT 全綠端對端證明。

## 2. Condition-skip 盤點（2 項，皆正當 — Iron Law §1/§3）

| sub-step | artifact | 依據 | 正當性 |
|---|---|---|---|
| harden-stubs | O-20 | EDD 無 §External Dependencies/§Degraded Path；0 reachable_locally=false 服務；無 blueprint/ 或 external-*.json；API 無 §External APIs | 唯一外部系統 = 金流供應商，ARCH §13 Phase B 預留（「Phase A 不依賴」），Phase A 假付費走內部 DepositConfirmed event。為 Phase-B 預留 provider 生 stub = 捏造未定義合約（Iron Law §1/§3）。Zero-on-both-sides，與 O-08/O-48 先例一致。另附 anti-stub 掃描 = 0。 |

兩項 skip 皆因上游 archConfig pin 明確 + 文件未定義對應 artifact，屬合法 zero-on-both-sides no-op，無 fabrication、無 gold-plating。

## 3. 無退化驗證（真跑，本輪實測）

- **單元**：`mvn -o -pl apps/api test` → **Tests run: 79, Failures: 0, Errors: 0, Skipped: 0, BUILD SUCCESS**（JDK21 / Maven 3.9.9，offline）。
- **整合**（真 postgres:16 + redis:7.2 Testcontainers，`-Dapi.version=1.43`，DOCKER_HOST npipe，Docker 29.1.3 Rancher Desktop）：`mvn -pl apps/api -Dit.test=RunBddContractIT,Jdbc*RepositoryIT,*SchemaIT,E2eSmokeJourneyIT failsafe:integration-test failsafe:verify` → **Tests run: 118, Failures: 0, Errors: 0, Skipped: 0, BUILD SUCCESS**；其中 RunBddContractIT **81 Scenarios (81 passed) / 489 Steps (489 passed)**，20 Jdbc*RepositoryIT + 16 *SchemaIT = 36 persist IT，+1 E2eSmokeJourneyIT。
- **合計**：**79 unit + 118 IT 全綠**，與 Harden phase 進入時基線一致，**零退化**。
- **衛生掃描**：`apps/api/src/main/java` 偷懶字眼（UnsupportedOperationException / NotImplemented / TODO / FIXME / not implemented / 先簡化 / 之後實作 / nice to have / minimum viable）= **0 命中**；test `@Disabled`/`@Ignore` = **0**；5 隻 harden/runbook shell `bash -n` 全 OK；4 隻 perf js `node --check` 全 OK。
- **laya submodule 未 touch**（git status 僅顯示 `../laya` modified pointer，未 stage，本次不動）。

## 4. Deviation 盤點

Harden phase 無 BLOCKING deviation。state.deviations[] 中 Harden 相關 4 筆皆 NICE-TO-HAVE：

- `harden-stubs / external_service_stubs`：DEFAULT-APPLIED — 0 stubs（zero-on-both-sides，見 §2）。
- `harden-k8s-smoke / ingress_host_entrypoint`：CONDITION-NOTE — host :80 被無關 nginx 佔用，host smoke 走 Service NodePort；Ingress manifest 已於 in-cluster 證明正確（throwaway pod → traefik → /health/live 200）。環境限制非 manifest 缺陷。
- `harden-k8s-smoke / api_service_nodeport`：CONDITION-NOTE — overlay 偏好 NodePort 30088 被鄰居 trino-nodeport 佔用，runner 動態改派空閒 port（本次 30081）。共享 cluster 環境限制。
- `harden-k8s-smoke / migration_V010_flyway_history_fix`：CONDITION-NOTE — V010 `schema_migrations` → `CREATE TABLE IF NOT EXISTS`，跨步修復真實 42P07 缺陷；SCHEMA §3.9.8 定義該表即 Flyway history，Flyway 擁有它為設計意圖。literal 保留使 40-table quant-gate 不變。缺陷已解並驗證（13 migrations 全綠套用）。

以上皆 0 BLOCKING、無 Iron Law §1/§3/§4 違反。

## 結論

Harden phase 7/7 sub-step 全數完成（4 實作 real-run + 2 condition-skip + 本收尾 review）。硬化資產完整涵蓋 **E2E smoke（O-26，12 步旅程真跑）/ perf（O-27，k6 4 情境 SLO 逐字抄 test-plan）/ k8s-smoke（O-38，真部署本機 k3s RC=0 + HA pod-kill + 修復 V010 Flyway 衝突）/ runbook（O-41，4 腳本 + guide）**。Flyway 鏈 V001–V013 連續、40 表 quant-gate 不變；真部署證據齊備屬實；condition-skip（O-20/O-39）皆 zero-on-both-sides 正當；79 unit + 118 IT 全綠無退化；anti-stub 掃描 0；deviation 全 NICE-TO-HAVE/DEFAULT-APPLIED/CONDITION-NOTE，0 BLOCKING。

**`finding_total == 0` → Harden phase status = PASS。**

### PHASE_REVIEW_RESULT

```yaml
PHASE_REVIEW_RESULT:
  phase: Harden
  finding_total: 0
  critical: 0
  high: 0
  medium: 0
  findings: []
```
