# LOCAL_SETUP — 本機開發環境一鍵設定與執行說明

> 全文 zh-TW。本文件是 **yoCat Office 後端信任層（`yocat-office-backend`）** 的「在地化執行說明」：從全新 clone 到服務就緒的最短路徑，含前置工具（JDK 21 / Maven / Docker / kubectl）安裝與驗證、一鍵腳本、逐步手動流程、存取 URL 與故障排除。
>
> **定位**：本文件是**入口指南**；完整的本機部署真相來源（SSOT，K8s 資源、HA、Tunnel、CI 本地模擬等）為 [`../../docs/LOCAL_DEPLOY.md`](../../docs/LOCAL_DEPLOY.md)。本文件與其對齊，不重複其細節。
>
> **對應需求**：PRD **US-PLAT-001 / AC-5**（[`../../docs/PRD.md`](../../docs/PRD.md)）——「全新 clone 的開發環境，於 Rancher Desktop k8s 一鍵部署 + 測試，全流程本機跑完、測試全綠，無需外部雲端（local-first）」。

---

## Document Control

| 欄位 | 內容 |
|------|------|
| **DOC-ID** | LOCAL_SETUP-YOCAT-OFFICE-BACKEND |
| **專案** | yoCat Office 後端信任層（`yocat-office-backend`，`client_type=api-only`） |
| **技術棧（權威：ARCH §11 / EDD §3.3）** | Java 21 (Temurin) + Spring Boot 3.3 + Spring Modulith 1.3 + Spring Data JDBC + PostgreSQL 16 + Redis 7.2 + Flyway；Kubernetes 1.30（Rancher Desktop k3s）；CI = GitHub Actions |
| **工作目錄** | `<project-root>/java-backend`（Makefile / `scripts/` / `k8s/` / `docker-compose.yml` 皆在此） |
| **一鍵腳本** | [`../scripts/local-setup.sh`](../scripts/local-setup.sh) |
| **完整部署 SSOT** | [`../../docs/LOCAL_DEPLOY.md`](../../docs/LOCAL_DEPLOY.md) |


---

## 1. 前置需求（Prerequisites）

本機基準：**Windows 11 + Rancher Desktop（containerd）+ Git Bash**。macOS / Linux 同樣支援（指令一致，安裝方式見各列）。安裝並以「驗證指令」確認後再往下。版本為**最低版本**（非「最新」）；以 ARCH §11 技術棧為權威。

| 工具 | 最低版本 | 類別 | 安裝（Windows / macOS / Linux） | 驗證指令 |
|------|---------|------|--------------------------------|---------|
| **JDK（Eclipse Temurin）** | 21 (LTS) | HARD | [adoptium.net](https://adoptium.net) ／ `brew install --cask temurin@21` ／ `apt install temurin-21-jdk` | `java -version`（顯示 `21`） |
| **Maven** | 3.9 | HARD | [maven.apache.org](https://maven.apache.org) ／ `brew install maven` ／ `apt install maven` | `mvn -v` |
| **Rancher Desktop** | 1.13 | HARD（k8s 模式） | [rancherdesktop.io](https://rancherdesktop.io)（Container Engine 選 **containerd**，Kubernetes 1.30） | `rdctl version` |
| kubectl | 1.30 | HARD（k8s 模式） | 隨 Rancher Desktop 內建 | `kubectl version --client` |
| Docker engine | — | HARD（compose / Testcontainers） | 隨 Rancher Desktop（`dockerd`）｜或 Docker Desktop | `docker version` |
| nerdctl | 1.7 | HARD（k8s 影像建置） | 隨 Rancher Desktop 內建 | `nerdctl version` |
| Git | 2.40 | HARD | [git-scm.com](https://git-scm.com)（含 Git Bash + openssl） | `git --version` |
| Make | 4.3 | 建議 | Git Bash：`choco install make` ／ `brew`（內建）／ `apt install make` | `make --version` |
| psql client | 16 | 建議 | `choco install postgresql16` ／ `brew install libpq` ／ `apt install postgresql-client-16` | `psql --version` |
| jq | 1.7 | 建議 | `choco install jq` ／ `brew install jq` ／ `apt install jq` | `jq --version` |
| k9s | 0.32 | 選配 | `choco install k9s` ／ `brew install k9s` | `k9s version` |

> **Rancher Desktop 設定**：Preferences → Virtual Machine → Hardware 至少 **8 GB RAM / 4 CPU**（API 2 副本 + PostgreSQL + Redis）；Container Engine 選 **containerd（nerdctl）**；Kubernetes 版本 1.30。
>
> **Windows PATH（Git Bash）**：Rancher Desktop 工具在 `~/Program Files/Rancher Desktop/resources/resources/win32/bin`。若 `kubectl` / `nerdctl` 找不到：
> ```bash
> export PATH="/c/Program Files/Rancher Desktop/resources/resources/win32/bin:$PATH"
> ```
> JDK / Maven 若已安裝但不在 PATH：設定 `JAVA_HOME` 並把 `$JAVA_HOME/bin`、Maven 的 `bin/` 加入 PATH（一鍵腳本的前置檢查會以 `JAVA_HOME` 為後備偵測 JDK）。

### 一行前置檢查（dry-run，不改任何東西）

```bash
cd <project-root>/java-backend
./scripts/local-setup.sh --check     # 只跑 JDK21 / Maven / Docker / kubectl 前置檢查並印報告
# 或完整工具鏈報告：
INFRA=k8s-local CLIENT=none LANG_STACK=java ./scripts/prereq-check.sh
```

---

## 2. 一鍵設定（One-Click Setup）

從全新 clone 到服務就緒，一行完成（前置檢查 → 假密碼 → 建置影像 → 套用 K8s → 遷移 + seed → 健康檢查 + HA 副本斷言 → 冒煙 → 印出存取 URL）：

```bash
cd <project-root>/java-backend

# K8s-local（Rancher Desktop，預設，主架構）
./scripts/local-setup.sh

# Docker Compose 後備（單機、不需 k8s；對齊 LOCAL_DEPLOY §19）
./scripts/local-setup.sh --compose

# 只做前置檢查（dry-run，不變更環境）
./scripts/local-setup.sh --check

# 其他旗標
./scripts/local-setup.sh --no-smoke     # 跳過 register→topup→spend 冒煙
./scripts/local-setup.sh --skip-build    # 沿用既有影像 / jar
./scripts/local-setup.sh -h              # 說明
```

成功時尾端輸出（K8s 模式）：

```
== Environment ready ==
  API (Ingress)   http://yocat-office-backend.local/
  Readiness       http://yocat-office-backend.local/health/ready
  Gateway (單一入口) http://localhost:30088/
  PostgreSQL      localhost:30432  (db=yocat user=yocat)
  Redis           localhost:30379
  OK  API Running replicas = 2 (>= 2, minimal HA)
  OK  smoke passed
```

> 腳本只**編排**既有資產（`scripts/bootstrap-secrets.sh` + Makefile targets + `docker-compose.yml` + `.env.local.example`），不自創任何部署邏輯；任一步失敗時，依下方 §6 故障排除處理。

---

## 3. 逐步手動流程（Step-by-Step）

一鍵腳本的等同手動步驟。所有指令於 `<project-root>/java-backend`（Git Bash）執行。

```bash
# 0) Clone + 進入工作目錄
git clone git@github.com:sayyogames/yocat-office-backend.git
cd yocat-office-backend/java-backend     # 本機即 <project-root>/java-backend
git status                               # Expected: On branch main

# 1) 前置檢查（見 §1）
./scripts/local-setup.sh --check

# 2) 環境變數範本 → 本機檔（host 直跑時用；K8s 由 ConfigMap/Secret 提供）
cp .env.local.example .env.local         # .env.local 已被 .gitignore；填妥 REQUIRED 後：
# set -a; . ./.env.local; set +a

# 3) 確認 kubectl context + 生成本機假密碼（gitignored，不印出）
kubectl config use-context rancher-desktop
./scripts/bootstrap-secrets.sh           # → k8s/overlays/local/secrets.env（隨機假值）

# 4) 既有 PostgreSQL（namespace yocat）+ app namespace/Secret/Redis
make db-up
make k8s-init

# 5) 建置 API 影像（nerdctl → k3s 的 containerd k8s.io namespace）
make image-build-api

# 6) 部署 API（≥2 replicas）+ Redis + Service + Ingress，等待就緒
make k8s-apply
kubectl wait --for=condition=Ready pods --all -n yocat-office-backend-local --timeout=300s

# 7) 資料庫初始化：Flyway migrate + seed（指令真實存在於 Makefile）
make db-migrate
make db-seed

# 8) 健康檢查 + HA 副本數
make health-check
kubectl get pods -l app=yocat-office-backend-api -n yocat-office-backend-local | grep -c Running   # Expected: ≥ 2

# 9) 冒煙（register → topup → spend → 402）
./scripts/local-setup.sh --skip-build --no-smoke >/dev/null 2>&1 || true   # 已部署時
#   或直接開 URL（見 §4）
```

### 3.1 開發內循環（host 直跑，秒級熱重載）

不必每次改碼都重建影像。API 在 host 以 Maven 跑，透過 NodePort 連叢集內 PostgreSQL（30432）與 Redis（30379）：

```bash
make db-up
kubectl apply -k k8s/overlays/local/     # 至少讓 redis 起來

DATABASE_URL="jdbc:postgresql://localhost:30432/yocat" \
DATABASE_USER="yocat" \
DATABASE_PASSWORD="$(kubectl get secret yocat-pg -n yocat -o jsonpath='{.data.POSTGRES_PASSWORD}' | base64 -d)" \
REDIS_HOST="localhost" REDIS_PORT="30379" \
  make dev                               # == mvn -pl apps/api spring-boot:run

curl -s http://localhost:8080/health/ready | jq .
```

### 3.2 Server Entrypoint（啟動點）

Spring Boot 啟動主類（`public static void main`）：

```
apps/api/src/main/java/com/sayyogames/yocat/YocatOfficeBackendApplication.java
```

啟動方式（擇一）：`make dev`（熱重載）／ `make build && make up`（背景跑打包 jar，`make down` 以 SIGTERM 優雅關閉）／ K8s（§2、§3）。監聽 port 由 `SERVER_PORT`（預設 8080）決定。

### 3.3 環境變數參考

完整環境變數目錄（每個 `${VAR}` 對應 `apps/api/src/main/resources/application.yml`）：[`../.env.local.example`](../.env.local.example)。涵蓋外部依賴：**PostgreSQL**（`DB_HOST`/`DB_NAME`/`DB_USER`/`DB_PASSWORD`）、**Redis**（`REDIS_HOST`/`REDIS_PORT`/`REDIS_PASSWORD` 及 pool/sentinel）、session/加密金鑰（`SESSION_SIGNING_KEY`/`ENCRYPTION_KEY`）等。K8s 部署不讀此檔——值來自 ConfigMap + Secret（`k8s/overlays/local/`），密碼由 `scripts/bootstrap-secrets.sh` 以 `openssl rand` 生成。

---

## 4. 存取 URL（Access）

需在 `C:\Windows\System32\drivers\etc\hosts`（系統管理員）加入：`127.0.0.1   yocat-office-backend.local`

| 服務 | URL / 位址 | 說明 |
|------|-----------|------|
| API（Ingress） | `http://yocat-office-backend.local/` | REST API（path-prefix，無 `/api` 前綴） |
| Readiness | `http://yocat-office-backend.local/health/ready` | 就緒檢查（含 DB/Redis），回 `{"status":"ok",...}` |
| Liveness | `http://yocat-office-backend.local/health/live` | 存活檢查 |
| Metrics | `http://yocat-office-backend.local/actuator/prometheus` | Micrometer 指標 |
| Gateway（單一入口） | `http://localhost:30088/` 或 `make pf-api` | 免 hosts 直連 |
| PostgreSQL | `localhost:30432`（db/user `yocat`）或 `make pf-db` | 既有 yocat-pg |
| Redis | `localhost:30379` 或 `make pf-redis` | — |

端點清單（含請求/回應）：[`../../docs/API.md`](../../docs/API.md)；資料表 / schema：[`../../docs/SCHEMA.md`](../../docs/SCHEMA.md)。

---

## 5. 測試與驗證（Tests）

CI 與本機跑**完全相同**的 Make targets（`docs/CICD.md §4`；避免「本機過、CI 掛」）：

```bash
make test-unit            # 單元測試（Surefire *Test.java；不需容器）
make test-integration     # 整合測試（Failsafe *IT，Testcontainers 起真實 postgres:16 + redis:7.2）
make test-bdd             # Cucumber BDD（RunBddContractIT）
make test-modulith        # Spring Modulith 邊界驗證（HC-1~HC-5）
make test-schema-isolation
```

> Testcontainers 對 Rancher Desktop 需 `DOCKER_HOST=npipe:////./pipe/docker_engine` 與 docker-java API 版本 `-Dapi.version=1.43`（Makefile `IT_FLAGS` 已內建；見 `make test-integration`）。
> 本專案 `client_type=api-only`：**無 Playwright / 前端 E2E**；關鍵路徑以 REST-assured + BDD 覆蓋。

---

## 6. 故障排除（Troubleshooting）

| 症狀（Symptom） | 原因（Root Cause） | 解法（Fix） |
|-----------------|-------------------|-------------|
| `java: command not found` 或前置檢查報 JDK < 21 | JDK 未裝或不在 PATH | 裝 Temurin 21（[adoptium.net](https://adoptium.net)）；設 `JAVA_HOME` 並把 `$JAVA_HOME/bin` 加 PATH。腳本 `--check` 會以 `JAVA_HOME` 後備偵測 |
| `kubectl: connection refused` / `--check` 報無節點 | Rancher Desktop k3s 未啟動 | 開 Rancher Desktop GUI → Kubernetes 頁確認 Ready；`kubectl config use-context rancher-desktop` |
| `ImagePullBackOff` | 影像未進 k3s 的 `k8s.io` containerd namespace | `nerdctl --namespace k8s.io images \| grep yocat-office-backend`；重跑 `make image-build-api`（含 `--namespace k8s.io`） |
| API Pod `CrashLoopBackOff` | 設定錯 / DB 未就緒 / Flyway 失敗 | `kubectl logs <pod> -n yocat-office-backend-local --previous`；DB 未就緒見下一列；migration 失敗 `make db-reset`（本機銷毀重建） |
| `password authentication failed for user "yocat"` | app Secret 與 yocat-pg Secret 密碼不一致 | 重跑 `./scripts/bootstrap-secrets.sh`（會對齊密碼）→ `make k8s-init && make k8s-restart-api` |
| `curl: Could not resolve host: yocat-office-backend.local` | hosts 未設定 | 於 `C:\Windows\System32\drivers\etc\hosts` 加 `127.0.0.1 yocat-office-backend.local`（或改用 NodePort `http://localhost:30088`） |
| API Running 副本數 < 2 | `replicas` 被改成 1 | 確認 `k8s/overlays/local/api-deployment.yaml` `replicas: 2`；`kubectl scale deploy/yocat-office-backend-api --replicas=2 -n yocat-office-backend-local` |
| `make test-integration` 失敗（Testcontainers） | docker daemon 未達 / API 版本協商失敗 | 啟動 Rancher Desktop container engine；確認 `DOCKER_HOST=npipe:////./pipe/docker_engine` 且帶 `-Dapi.version=1.43`（Makefile 已內建） |
| `--compose` 模式 db 一直非 healthy | DB_PASSWORD 未設 / port 30432 被占用 | 確認 `.env` 內 `DB_PASSWORD` 有值（腳本會自動生成）；釋放占用 30432 的程序 |

更多（HikariCP 連線池、Redis maxmemory、Flyway checksum、k9s、Tunnel、act CI 本地模擬）見 [`../../docs/LOCAL_DEPLOY.md`](../../docs/LOCAL_DEPLOY.md) §10 / §20 / §21。

---

## 7. 清理（Cleanup）

```bash
make k8s-delete      # 移除 app 資源（保留既有 PostgreSQL 與 PVC）
make k8s-clean       # 刪整個 app namespace（不動 yocat DB）
make db-reset        # drop + 重建 7 BC schema → 重新 migrate + seed（銷毀本機資料）
# Compose：docker compose down（保留 volume）／ docker compose down -v（完全重置）
```

---

## 相關文件（Cross-References）

- [`../../README.md`](../../README.md) — 專案總覽
- [`../../docs/LOCAL_DEPLOY.md`](../../docs/LOCAL_DEPLOY.md) — 本機部署 SSOT（K8s / HA / Tunnel / CI 本地模擬）
- [`../../docs/PRD.md`](../../docs/PRD.md) — US-PLAT-001 / AC-5（local-first 驗收）
- [`../../docs/API.md`](../../docs/API.md) — REST 端點契約
- [`../../docs/SCHEMA.md`](../../docs/SCHEMA.md) — 資料表 / schema-per-BC
- [`../../docs/ARCH.md`](../../docs/ARCH.md) §11 — 技術棧全覽（版本權威）
- [`../Makefile`](../Makefile) — 開發 + CI 共用 Make targets
- [`../scripts/local-setup.sh`](../scripts/local-setup.sh) — 本文件配套的一鍵腳本

---

## Self-Check（生成後自檢）

| 檢查項 | 結果 |
|--------|------|
| Prerequisites 涵蓋 ARCH §11 全部工具，含具體最低版本（非「最新」） | ✅ |
| 每個工具有官方安裝連結（標 Windows/macOS/Linux）+ 版本驗證指令 | ✅ |
| Step-by-step：clone → 前置檢查 → cp .env → migrate → seed → dev → URL → 冒煙 | ✅ |
| DB 初始化命令（`make db-migrate` / `make db-seed`）真實存在於 Makefile | ✅ |
| Server entrypoint 實際路徑引用（`YocatOfficeBackendApplication.java`，含 main） | ✅ |
| 環境變數範本 `.env.local.example` 涵蓋 PostgreSQL / Redis / 金鑰 等外部依賴 | ✅ |
| 故障排除 ≥ 5（症狀 → 原因 → 解法 三段式；本表 9 項） | ✅ |
| 交叉連結 README / API / SCHEMA / PRD / ARCH / LOCAL_DEPLOY / Makefile（路徑有效） | ✅ |
| 無 gold-plating（只涵蓋 ARCH 工具 + PRD US 場景；細節委由 LOCAL_DEPLOY SSOT） | ✅ |
