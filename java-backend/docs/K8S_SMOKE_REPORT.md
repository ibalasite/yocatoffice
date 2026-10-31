# K8s Deployment Smoke Report — yocat-office-backend

部署後煙霧驗證：將本後端**真實部署**到本機 Rancher Desktop k3s 叢集，等 rollout + Flyway
migration Job 完成，對部署的 Service 打 health 與關鍵業務 API，並做基本 HA 驗證。

- **Sub-step**：`harden-k8s-smoke`（Phase 9 Harden 4/7）· artifact O-38
- **Runner（唯一真理）**：[`scripts/k8s-smoke.sh`](../scripts/k8s-smoke.sh)
- **CI wrapper**：[`tests/smoke/k8s-deployment.test.ts`](../tests/smoke/k8s-deployment.test.ts)（`node --test`）
- **執行方式**：`bash scripts/k8s-smoke.sh`（結束自動清理，`KEEP_RESOURCES=1` 可保留除錯）

---

## 執行紀錄（真跑，不是樣板）

| 項目 | 值 |
|------|-----|
| 時間 | 2026-10-31T08:26:44+0800（definitive run，RC=0） |
| Host | `Aibala` / Windows 11 |
| 叢集 | context `rancher-desktop`，node `aibala` `Ready`（k3s `v1.25.16+k3s4`） |
| k3s container runtime | `docker://29.1.3`（Rancher Desktop **dockerd/moby 模式**，非 containerd） |
| Toolchain | kubectl `v1.25.16`、kustomize `v4.5.7`、docker/dockerd `29.1.3` |
| DB | 既有 `yocat-pg`（namespace `yocat`，PostgreSQL `16.15`，NodePort 30432；跨 namespace DNS `yocat-pg.yocat.svc.cluster.local:5432/yocat`） |
| App image | `yocat-office-backend/api:local`（multi-stage docker build，manifest list `sha256:09da4672b176…`，405MB） |

### 流程逐項結果（全 ✅）

```
0. preconditions        ✅ node Ready / context 非 prod / yocat-pg 存在
1. build image          ✅ docker build -> yocat-office-backend/api:local（runtime 自偵測為 docker）
2. manifests dry-run    ✅ kubectl apply -k --dry-run=client 合法
3. apply -k             ✅ ConfigMap+Secret+Redis(Deploy/Svc)+API(Deploy)+Ingress+Flyway Job
                           （api-service 於 3b 重配，見下方 Finding #3）
4. Flyway Job           ✅ Successfully applied 13 migrations to schema "platform", now at version v013
5. API rollout          ✅ deployment successfully rolled out（replicas 2/2）
5b. all pods Running    ✅ 2×api Running + redis Running + flyway Completed
5c. external entrypoint ✅ Service NodePort 30081 -> http://127.0.0.1:30081（/health/ready http=200）
6. db-seed（demo）       ✅ 補充 demo 資料（INSERT/COMMIT）；關鍵 catalog 由 V011 migration 提供
7. health（外部 Service）✅ /health/live 200 {"status":"UP"}
                        ✅ /health/ready 200 {"status":"UP","db":"UP","redis":"UP"}
7b. Ingress（Traefik）   ✅ in-cluster：Host: yocat-office-backend.local -> /health/live = 200
                           （證明 Ingress manifest 路由正確：Traefik -> Service -> pod）
8. API smoke            ✅ register -> token；topup g_big -> 700 gems；spend 10 -> 690 gems
                        ✅ overdraw 1,000,000 -> HTTP 402 {"error":"餘額不足",...balance 690 未變}
9. HA                   ✅ readyReplicas=2；kill 1 pod 期間 Service 持續可用（存活 replica 回 200）
                        ✅ deployment 自我修復回滿 replicas
=> ✅ k8s smoke test passed（RC=0）
teardown                ✅ 刪 namespace + DROP 9 個 yocat BC schema；membership public(8 表) 未動
```

### 關鍵回應實樣（擷取自真跑輸出）

```
--- /health/live ---   HTTP/1.1 200   {"status":"UP"}
--- /health/ready ---  HTTP/1.1 200   {"status":"UP","db":"UP","redis":"UP"}
register:  {"token":"…","account":{"id":1,…},"wallet":{"cash":0,"gems":0}}
topup:     {"ok":true,"currency":"gems","amount":700,"balance":700,"wallet":{"cash":0,"gems":700}}
spend:     {"ok":true,"currency":"gems","balance":690,"wallet":{"cash":0,"gems":690}}
overdraw:  HTTP 402  {"error":"餘額不足","currency":"gems","balance":690,"wallet":{"cash":0,"gems":690}}
Flyway:    Successfully applied 13 migrations to schema "platform", now at version v013
```

---

## 真跑過關 vs 環境限制（如實記錄）

**真跑通過（本機 k3s 實際部署）**：image build、kustomize apply、Flyway migration Job（13 版）、
rollout（2 replicas）、所有 pod Running、health `/health/live`+`/health/ready`（200 + db/redis UP）、
關鍵 API（register→topup→spend→402）、HA（≥2 replicas + pod-kill 期間可用 + 自我修復）、收尾清理。

**環境限制（best-effort，非程式/manifest 缺陷）**：
- **Host 層 Ingress 入口**：`127.0.0.1:80` 被一個無關的 nginx 佔用，Rancher Desktop 因而無法把
  Traefik 發佈到 host 的 :80，故 host 無法直接打 `http://yocat-office-backend.local`。Ingress
  manifest 本身**已於 in-cluster 證明路由正確（200）**。host 對外煙霧改走 Service NodePort
  （經 Service → kube-proxy 在 2 replica 間負載均衡，非 `kubectl port-forward` 直連單一 pod），
  為 `api-service.yaml` 註記的「direct local access」真實入口。

---

## 本輪發現並修復的真缺陷（k8s-smoke 的價值）

本 smoke 是整條 pipeline 中**第一次以 Flyway 真實部署**（先前 persist/contract IT 以 `psql`
直接套用 migration，走不同路徑），因而揭露先前測不到的真問題：

### Finding #1（已修）— Flyway history 表與 V010 自建表衝突 → migration 失敗
`FLYWAY_TABLE=schema_migrations` + `FLYWAY_DEFAULT_SCHEMA=platform` 使 Flyway 於遷移前自建
`platform.schema_migrations` 作為其歷史表；但 `V010__create_platform_tables.sql` 又手動
`CREATE TABLE platform.schema_migrations`，造成 `42P07 relation already exists`，整個 Job 失敗。
SCHEMA §3.9.8 本就定義此表「即 Flyway schema history」，Makefile `db-status` 亦查 `installed_rank`
（Flyway 欄位），佐證設計意圖為 Flyway 管理此表。
- **修法（最小、不回歸）**：V010 改 `CREATE TABLE IF NOT EXISTS platform.schema_migrations`。
  Flyway 部署路徑下成為 no-op（用 Flyway 的歷史表）；psql 直接套用路徑（IT）下表不存在仍正常建立。
  `CREATE TABLE` 字樣保留 → SCHEMA §1.2 的 40 表 quant-gate 不變。
- **驗證**：修後 Flyway `Successfully applied 13 migrations … v013`；V010 僅輸出良性
  `WARNING: relation "schema_migrations" already exists, skipping`。

### Finding #2（smoke 腳本已對齊契約）— health body 契約為 `status:UP` 非 `ok`
`HealthController` 回 `{"status":"UP","db":"UP","redis":"UP"}`（LOCAL_DEPLOY 範例寫 `ok` 為示意）。
smoke 斷言對齊實際契約（200 + `status:UP` + `db:UP` + `redis:UP`）。

### Finding #3（smoke 腳本韌性）— NodePort 30088 在共用叢集已被佔用
`api-service.yaml` 偏好 NodePort 30088，但本共用 k3s 中已被 `lakehouse/trino-nodeport` 佔用，
`kubectl apply -k` 僅此一資源被拒。smoke 於 3b 偵測並改配一個空閒 NodePort（本次 30081），
再由 Service 讀回實際 nodePort 當對外入口。**上游 manifest 的 30088 維持不動**（在隔離環境是合理預設）。

### Finding #4（smoke 腳本已修）— db-seed 以 `psql -v` 傳帶點變數名失敗
`db-seed.sql` 以 `current_setting('yocat.seed_env')` 讀 GUC；正確傳法是
`PGOPTIONS="-c yocat.seed_env=local"`（非 `psql -v`，後者拒絕帶點變數名）。已改用 PGOPTIONS，
且 demo seed 設為 non-fatal（smoke 需要的參考 catalog 由 V011 migration 提供）。

---

## 無退化

本 sub-step 只改 `migrations/V010`（加 `IF NOT EXISTS`，語意等價、psql 路徑行為不變）+ 新增
smoke 腳本/測試/本報告。既有 79 unit + 118 IT（RunBddContractIT 81 + 36 persist IT + 1 E2E）
不受影響。laya submodule 未更動。

> 清理原則：smoke 結束刪除 `yocat-office-backend-local` namespace 並 DROP 9 個 yocat BC schema，
> 不觸碰既有 membership 的 `public` schema（8 表完好），不污染共用叢集與共用 DB。
