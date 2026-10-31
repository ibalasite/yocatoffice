# RUNBOOK_GUIDE — 維運腳本操作手冊（yocat-office-backend）

> 本文件說明 `scripts/runbook/` 下 4 支**可執行**維運腳本：**何時跑、預期輸出、失敗回退步驟**。
> 腳本是 [`docs/runbook.md`](../../docs/runbook.md)（營運 Runbook，權威真相）的機械化入口；
> 每支腳本 1:1 對應 Runbook 章節，不新增 Runbook 未定義的步驟。
> 內容來源：`docs/runbook.md` + 本專案實際產物（`HealthController.java`、`Makefile`、
> `k8s/overlays/local/*`、`migrations/`、`docs/K8S_SMOKE_REPORT.md` 實測發現）。

---

## 0. 共同約定（所有腳本）

- **Shell**：bash（`set -euo pipefail`，部分失敗不會被吞）。本機（Rancher Desktop / Git Bash）與 CI 共用。
- **工具**：`kubectl` / `curl` / `psql` / `redis-cli`（kubectl、docker 在 Rancher Desktop
  `C:\Program Files\Rancher Desktop\resources\resources\win32\bin`；請確認在 PATH，或以 `KUBECTL=/abs/path` 覆寫）。
- **無 hardcode**：namespace、deployment、URL 全由環境變數帶入，預設對齊 `Makefile`（本機 local-first）。
  常用覆寫變數：

  | 變數 | 預設（本機） | Prod 範例 | 來源 |
  |------|-------------|-----------|------|
  | `APP_NS` | `yocat-office-backend-local` | `yocat-prod` | Makefile `APP_NS` / `NS_prod` |
  | `DB_NS` | `yocat` | `yocat-prod` | Makefile `DB_NS`、docs/req/postgres.yaml |
  | `API_DEPLOY` | `yocat-office-backend-api` | 同左 | k8s/overlays/local/api-deployment.yaml |
  | `DB_DEPLOY` | `yocat-pg` | `yocat-pg` | docs/req/postgres.yaml |
  | `REDIS_DEPLOY` | `redis` | `redis-sentinel` | k8s/overlays/local/redis.yaml |
  | `BASE_URL` | `http://yocat-office-backend.local` | `https://api.yocat.sayyogames.com` | LOCAL_DEPLOY §Ingress（單一外部入口，Traefik :80）|
  | `MIN_REPLICAS` | `2` | `2` | Iron Constraint（EDD §3.7.2，HA 下限恆 ≥2）|

- **對外入口走 Ingress，不直連 pod**：health 類請求打 `BASE_URL`（Ingress host
  `yocat-office-backend.local`，LOCAL_DEPLOY 定義的單一外部入口）。若 host :80 被佔用
  （見 K8S_SMOKE_REPORT Finding #3），改走 Service NodePort：`BASE_URL=http://127.0.0.1:30088`。
- **真實 health 契約**（`HealthController.java` + K8S_SMOKE_REPORT Finding #2）：
  `GET /health/live → 200 {"status":"UP"}`；`GET /health/ready → 200 {"status":"UP","db":"UP","redis":"UP"}`，
  DB 不可達時回 `503 {"status":"DOWN",...}`。Redis 降級開放（EDD §8.3），不拉低 readiness。
  （Runbook/LOCAL_DEPLOY 散文中的 `"status":"ok"` 為示意；腳本以實際 `"UP"` 契約為準。）

---

## 1. `health-check.sh` — 健康檢查

- **對應 Runbook**：§2.2 元件清單健康檢查、§4.4 本機部署後驗證、§7.1/§7.2 故障排除首步。
- **何時跑**：部署後、事故偵測首 5 分鐘、On-Call 交接前（Runbook §14）、任何「服務是否活著」的快檢。
- **指令**：
  ```bash
  bash scripts/runbook/health-check.sh
  # Prod：APP_NS=yocat-prod DB_NS=yocat-prod DB_DEPLOY=yocat-pg \
  #       BASE_URL=https://api.yocat.sayyogames.com bash scripts/runbook/health-check.sh
  ```
- **做什麼**：(1) curl Ingress `/health/live`+`/health/ready`（2 endpoint，HTTP 200 + `status:UP`）；
  (2) `kubectl get pods` API ≥2 Running；(3) `pg_isready` + `SELECT 1`；(4) `redis-cli ping`。
- **預期輸出**：4 區塊皆 `[OK]`，結尾 `✅ health-check PASS`，exit 0。
- **失敗回退**：
  - endpoint 503 → DB 不可達 → Runbook §7.5；endpoint 000（無路由）→ 查 `/etc/hosts` 的
    `yocat-office-backend.local` 與 traefik pod → §7.2。
  - pod < 2 Running → §7.6 CrashLoopBackOff（`kubectl logs <pod> --previous`）。
  - DB 失敗 → §7.5 / Patroni failover §7.5.2；Redis 失敗 → §7.7（cache 降級應仍回源，不致 500）。

---

## 2. `deploy-rollback.sh` — 部署回滾

- **對應 Runbook**：§9.1 應用版本回滾、§9.4 回滾後驗證（縮版）。
- **何時跑**：新版部署後出現 5xx 升高/健康劣化（Runbook §7.1 決策樹「近 15 分鐘有部署?→Yes」）、
  smoke 失敗（§4.2 Step 5 exit 2）、或任何需快速止血回前一版的情境。
- **指令**：
  ```bash
  bash scripts/runbook/deploy-rollback.sh                 # 回前一 revision
  TO_REVISION=7 bash scripts/runbook/deploy-rollback.sh    # 回指定 revision
  DRY_RUN=1 bash scripts/runbook/deploy-rollback.sh        # 只印步驟不實際 undo
  EXPECT_CONTEXT=prod-k8s bash scripts/runbook/deploy-rollback.sh  # 強制 context 守門（§9.1 Step 0）
  ```
- **做什麼**：context 守門 → `rollout history` → `rollout undo`（可指定 revision）→ `rollout status`
  → pods ≥2 → `health/ready` 200/UP → 錢包對帳 0 不一致（§7.8 SQL）。
- **預期輸出**：各步 `[OK]`，結尾 `✅ deploy-rollback 完成並通過驗證`，exit 0。
- **失敗回退**：
  - context 非預期 → `kubectl config use-context <ctx>` 後重跑（絕不在錯誤叢集操作）。
  - rollout 未完成 → `kubectl describe deployment/<api>` 查 Warning（ImagePull/OOM/readiness）→ §7.6。
  - health 失敗 → 依 db/redis 分流 §7.5 / §7.7。
  - 對帳不一致 → **立即 P0**，§7.8，並重新評估事故處置（Flyway migration 採 expand-contract，
    code 回滾不需 schema 回滾，§9.2）。

---

## 3. `disaster-recovery.sh` — 災難復原

- **對應 Runbook**：§10 備份與還原、§10.2 DB 還原、§10.3 還原後驗證、§11.3 Step 4 session 撤銷、§8.3 log 封存。
- **何時跑**：DB 毀損/資料遺失（P0）、PITR 還原演練（§15 場景2 每季 DR Drill）、
  credentials 洩漏需撤銷 session（§11.3）、事故證據封存。
- **子命令**：
  ```bash
  bash scripts/runbook/disaster-recovery.sh backup            # pg_dump -Fc → .runbook-backups/
  CONFIRM=1 bash scripts/runbook/disaster-recovery.sh restore <dump>   # pg_restore --clean --if-exists
  bash scripts/runbook/disaster-recovery.sh verify           # pg_isready + health db:UP + 對帳 0
  CONFIRM=1 bash scripts/runbook/disaster-recovery.sh redis-flush      # 清 member:session:*
  bash scripts/runbook/disaster-recovery.sh log-archive      # 封存近 2000 行 api log
  ```
- **安全護欄**：`restore` 與 `redis-flush` 為破壞性，預設拒絕，需 `CONFIRM=1`；還原請指向**隔離目標**，
  勿覆寫現役 Primary（§10.2）。密碼由 pod 內 Secret 注入，命令行不帶明文。
- **預期輸出**：對應子命令 `✅`（backup 產出非空 dump；verify 三檢全 `[OK]`）。
- **失敗回退**：
  - backup 產物為空 → 確認 DB 連線 §7.5。
  - restore 後 verify 未過 → 改用前一全量備份點重試（§10.2）；對帳不一致 → §7.8，以 ledger 為權威。
  - redis-flush 後 → DB 保有 session 權威 TTL，快取 miss 回源不致 500；接著查 Audit Log §11.5。

---

## 4. `daily-inspection.sh` — 日常巡檢

- **對應 Runbook**：§8 例行維護、§8.2 DB 維護、§8.4 容量審查、§7.9 磁碟壓力、§7.4 Outbox、§7.8 對帳。
- **何時跑**：每日/每班巡檢、On-Call 交接前填 Handoff（§14）的「錢包對帳 / Error Budget / 待追蹤」欄位。
- **指令**：
  ```bash
  bash scripts/runbook/daily-inspection.sh
  ```
- **做什麼**（唯讀，每項獨立不中斷）：(1) pods ≥2；(2) HPA/replicas + `kubectl top` 容量快照；
  (3) DB `df` 磁碟 < 85%；(4) 錢包對帳 0 不一致 + `modulith.event_publication` Outbox pending < 100；
  (5) `leaderboard` 索引 `idx_scan`；(6) 近 500 行 ERROR/Exception 計數。
  （§8.3 log 輪換由 K8s/containerd 自動管理，腳本只巡檢錯誤量、**不**手動 rotate。）
- **預期輸出**：各項 `[OK]`，結尾 `✅ daily-inspection：全部健康`，exit 0；有異常則 `[WARN]/[FAIL]` 並 exit 1。
- **失敗回退**：對帳不一致 → P0 §7.8；Outbox 積壓 → §7.4；磁碟 ≥85% → §7.9（WAL 堆積/擴容 PVC）；
  ERROR 升高 → §7.1 讀 log 辨識來源 BC。

---

## 5. 腳本 ↔ Runbook 章節對照（驗證用）

| 腳本 | 主要 Runbook 章節 | 真實產物引用 |
|------|-------------------|--------------|
| `health-check.sh` | §2.2 / §4.4 / §7.1 / §7.2 | `/health/live`+`/health/ready`（HealthController）、`yocat-pg`、`redis` |
| `deploy-rollback.sh` | §9.1 / §9.4 / §7.6 / §7.8 | `kubectl rollout undo/status`、wallet 對帳 SQL（V003 schema）|
| `disaster-recovery.sh` | §10.2 / §10.3 / §11.3 / §8.3 | `pg_dump/pg_restore`（yocat-pg）、`member:session:*`、`kubectl logs` |
| `daily-inspection.sh` | §8.2 / §8.4 / §7.9 / §7.4 / §7.8 | HPA、`df`、`modulith.event_publication`（V009）、`pg_stat_user_indexes` |

---

## 6. 相關 Makefile 入口（本機/CI 共用）

本腳本與 `Makefile` 互補，可交叉使用（`Makefile` 為 §6/§13 的主要維運動作來源）：

| 需求 | Makefile target | 關係 |
|------|-----------------|------|
| 快速健康檢查 | `make health-check` | curl `/health/ready` + 列 pods（本腳本 §1 的簡版）|
| 列 pod / tail log | `make pods` / `make logs-api` | 巡檢輔助 |
| 部署 / 重啟 / 回滾 | `make k8s-apply` / `make k8s-restart-api` / `make ci-rollback` | §4 / §9.1 |
| DB 遷移 / seed / 狀態 | `make db-migrate` / `make db-seed` / `make db-status` | §7.10 / §9.2 |
| HA 驗證 | `make cd-verify-ha` | §4 rollout + 斷言 ≥2 replicas |
| 部署煙霧測試 | `make ci-smoke` / `bash scripts/k8s-smoke.sh` | §4.2 Step 5、K8S_SMOKE_REPORT |

---

> **演練（Runbook §15）**：本手冊的腳本應於 Staging 定期演練（月度連線池耗盡、季度 DR Drill、
> 新 On-Call 走部署+回滾）。每次演練後更新 `docs/runbook.md` 文件控制表「最近演練」欄位。
