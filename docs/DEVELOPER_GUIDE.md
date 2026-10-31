---
doc-type: DEVELOPER_GUIDE
version: "1.0"
status: "draft"
project: "yocat-office-backend"
upstream-docs:
  required:
    - LOCAL_DEPLOY.md
    - CICD.md
    - runbook.md
  recommended:
    - SCHEMA.md
    - EDD.md
    - ARCH.md
    - API.md
    - CONSTANTS.md
    - diagrams/class-inventory.md
---

# Developer Guide — yocat-office-backend（yoCat Office 後端信任層）

> **本文件描述** yocat-office-backend 的日常開發操作與模組化開發規範。前提：已按 [LOCAL_DEPLOY.md](LOCAL_DEPLOY.md) 完成初始環境建置。
>
> **適用對象**：已完成 LOCAL_DEPLOY.md 建置的後端工程師，需要快速查找日常操作、理解 Spring Modulith 7 BC 模組邊界、並依 Clean Architecture + HC-1~HC-5 規範新增/修改功能。
>
> **技術棧**：Java 21 (LTS) + Spring Boot 3.3 + Spring Modulith 1.3 + PostgreSQL 16 + Redis 7.2；base-package = `com.sayyogames.yocat`；repo root = `<project-root>`。
>
> **NOTE（CI/CD 工具對齊）**：本專案 CI = GitHub Actions / 本機 Gitea Actions（`act_runner`），CD = ArgoCD 2.12（GitOps）。本文件 §2 CI 診斷依此實際工具撰寫（**非 Jenkins**），來源 [CICD.md](CICD.md) §1.1/§6/§8。
> **NOTE（Make target 來源）**：Make target 名稱以 [LOCAL_DEPLOY.md](LOCAL_DEPLOY.md) §6 為主、CI 包裝（`ci-*`）以 [CICD.md](CICD.md) §4 為 Source of Truth。
> **NOTE（DB Migration 工具）**：[SCHEMA.md](SCHEMA.md) §8 明定 migration-tool = **Flyway**，故本文件 §1 場景 B 與 §4 FAQ 僅保留 Flyway 指令。

---

## Document Control

| 欄位 | 值 |
|------|---|
| **產品名稱** | yocat-office-backend（yoCat Office 後端信任層） |
| **適用環境** | Local（Rancher Desktop k3s，namespace `yocat-office-backend-local`） |
| **前置條件** | LOCAL_DEPLOY.md 初始建置已完成（JDK 21 / Rancher Desktop / Secret Bootstrap） |
| **技術棧** | Java 21 + Spring Boot 3.3 + Spring Modulith 1.3 + PostgreSQL 16 + Redis 7.2 |
| **base-package** | `com.sayyogames.yocat` |
| **Author** | Evans Tseng |
| **Last Updated** | 2026-10-30 |
| **Status** | draft |

---

## §1 日常開發工作流程

### 1.1 啟動開發環境

```bash
# 從 repo root 執行（<project-root>），Git Bash
# 0. 確認 Rancher Desktop 已執行（kubectl 可連 k3s）
kubectl cluster-info

# 1. 確保 PostgreSQL（既有 yocat namespace）
make db-up

# 2. 部署 Redis + API + Service + Ingress（namespace yocat-office-backend-local）
make k8s-apply

# 3. 確認所有 Pod 就緒
kubectl get pods -n yocat-office-backend-local

# 4. Port-forward 到本地（API 8080 / DB 5432 / Redis 6379；背景執行並記 PID）
make pf-api

# 5. 健康檢查
make health-check   # 檢查 GET /health/ready（member/wallet 所用 DB + Redis 連線）
```

### 1.2 六大日常開發場景

#### 場景 A：修改 API 邏輯並驗證

```bash
# 1. 修改程式碼（例：wallet/application/SpendWalletUseCase.java）
# 2. 執行單元測試（JUnit 5 + Mockito，不需 K8s）
make ci-test-unit

# 3. 重新建置 image + 重啟部署
make image-build-api            # nerdctl build -t yocat-office-backend/api:local
make k8s-restart-api            # kubectl rollout restart deploy/yocat-office-backend-api

# 4. 確認部署成功
kubectl rollout status deployment/yocat-office-backend-api -n yocat-office-backend-local

# 5. 執行 smoke test
make ci-smoke
```

#### 場景 B：新增資料庫 Migration（Flyway）

```bash
# migration-tool = Flyway（SCHEMA.md §8）。命名規則：V{n}__{description}.sql
# 1. 新增 migration 檔案（置於對應 BC schema；各 BC 獨佔 schema，HC-1）
#    路徑：apps/api/src/main/resources/db/migration/V{n}__{description}.sql
#    範例：V12__wallet_add_idempotency_key.sql
#    注意：禁止跨 BC FK；跨 BC 參照以 account_id BIGINT（application-layer ID，無跨 schema FK）

# 2. 執行 migration（更新 DB Schema）
make db-migrate

# 3. 驗證 migration 狀態
make db-status

# 4. 重新部署
make k8s-restart-api
```

#### 場景 C：執行完整 CI Pipeline（本地模擬）

```bash
# 以 act / Gitea Actions 跑同一份 .github/workflows（Local-First，消除「本地 OK、CI 炸掉」）
make ci-dry-run                 # act pull_request -W .github/workflows/ci.yml

# 或單步執行（對齊 CICD.md §4 Shared Make Targets）
make ci-lint && make ci-archunit         # spotless + ArchUnit（Domain 不得 import spring/java.sql）
make ci-test-unit                        # JUnit + JaCoCo gate（≥80%）
make ci-test-integration                 # REST-assured + Testcontainers
make ci-test-modulith                    # HC-1~HC-5：0 循環依賴、BC 邊界完整
make ci-contract-test                    # WalletService.debit() 等合約
make ci-smoke
```

#### 場景 D：查看 Log 和除錯

```bash
# 查看 API 服務 log
kubectl logs -f deployment/yocat-office-backend-api -n yocat-office-backend-local

# 進入 API Pod debug
kubectl exec -it deployment/yocat-office-backend-api -n yocat-office-backend-local -- /bin/sh

# 使用 k9s（互動式 K8s UI）
k9s -n yocat-office-backend-local
```

#### 場景 E：重設開發環境（出問題時）

```bash
# 軟重設（重新部署，保留 PostgreSQL / PVC 資料）
make k8s-delete && make k8s-apply

# 硬重設（清除 app namespace，完全重建；不動既有 yocat DB）
make k8s-clean && make k8s-init && make k8s-apply
./scripts/bootstrap-secrets.sh          # k8s-clean / db-reset 後必須重跑（LOCAL_DEPLOY.md §3.5）

# 只重設資料庫（銷毀資料重跑 migration）
make db-reset && ./scripts/bootstrap-secrets.sh
```

#### 場景 F：提交 PR 前的自我檢查

```bash
# 對齊 CICD.md §5.3 Pre-PR Checklist（S1~S6 PR Gate 本機預跑）
make ci-lint && make ci-archunit                 # S1 lint + 架構守護
make ci-test-unit                                # S2 unit + JaCoCo ≥80%
make ci-test-integration                         # S3 integration（Testcontainers）
make ci-test-modulith && make ci-contract-test   # S4 Modulith 邊界 + 合約
make ci-spotbugs && make ci-semgrep && make ci-dependency-scan   # S5 靜態分析（High/Critical=0）
make ci-dry-run                                  # S6 完整 PR Gate 模擬
```

---

## §2 CI/CD 診斷與修復

> 本專案 CI = GitHub Actions（雲端）/ Gitea Actions `act_runner`（本機，`dev-tools` ns），CD = ArgoCD（GitOps）。Runner namespace 統一為 `yocat-office-backend-ci`（CICD.md §6）。

### 2.1 CI Pipeline 未觸發（git push 後無反應）

| 症狀 | 可能原因 | 診斷步驟 |
|------|---------|---------|
| push 後 Gitea Actions 無反應 | Webhook 未送達 runner | `curl -X POST http://runner.yocat-office-backend-ci.svc.cluster.local:8080/gitea-webhook/post` 測試 # Webhook URL 來源 CICD.md §8.4 |
| Webhook 回 200 但 workflow 不跑 | `act_runner` 未註冊或離線 | `kubectl get pods -n yocat-office-backend-ci`；確認 runner Pod Running，否則 `kubectl logs -n yocat-office-backend-ci deploy/act-runner` |
| runner namespace 不存在 | 自架 runner 未安裝 | `helm upgrade --install act-runner ... -n yocat-office-backend-ci -f k8s/dev-tools/gitea-values.yaml`（CICD.md §6.1） |
| GitHub Actions 雲端不跑 | self-hosted runner 標籤不符 | 確認 workflow `runs-on: [self-hosted, k3s]` 與 ARC runner label 一致（CICD.md §1.2） |

### 2.2 Pipeline 各 Stage 失敗排查

| Stage | 常見錯誤 | 修復方法 |
|-------|---------|---------|
| Lint | spotless 格式不符 | 本機 `make ci-lint`（`./mvnw -pl apps/api spotless:check`）；自動修復 `./mvnw spotless:apply` |
| ArchUnit | Domain 違規 import | `make ci-archunit`；移除 `domain/` 中的 `org.springframework.*` / `java.sql.*`（EDD §3.1b 禁止清單） |
| Unit Test | 測試失敗 / JaCoCo < 80% | `make ci-test-unit` 本機重現；補測試至覆蓋率門檻 |
| Integration Test | Testcontainers 連線失敗 | 確認 Docker/containerd 可用；`make ci-test-integration`（REST-assured + Testcontainers） |
| Modulith Test | HC-1~HC-5 邊界/循環違規 | `make ci-test-modulith`（`ApplicationModules.verify()` 擲 AssertionError）；見 §10 消除跨 BC 違規 |
| Image Build | Kaniko 失敗 | 確認 registry-credentials secret 存在：`kubectl get secret registry-credentials -n yocat-office-backend-ci`（self-hosted 禁 DinD，用 Kaniko） |
| Deploy | Pod 起不來 | `kubectl describe pod -n yocat-office-backend-local`；查 Event / image pull / 資源 |
| Smoke Test | endpoint 無回應 | 確認 `make pf-api` port-forward 執行中；`make health-check` |

### 2.3 ArgoCD 同步失敗（CD）

```bash
# 查看 ArgoCD 同步狀態
argocd app get yocat-office-backend

# 強制重新同步
argocd app sync yocat-office-backend

# 查看期望態 vs 實際態 diff
argocd app diff yocat-office-backend

# 本機透過 port-forward 進 ArgoCD UI（LOCAL_DEPLOY.md §外部存取）
kubectl port-forward svc/argocd-server -n argocd 8443:443   # → https://localhost:8443
```

---

## §3 Quick Reference 指令表

### 3.1 最常用指令

| 目的 | 指令 |
|------|------|
| 確保 PostgreSQL | `make db-up` |
| 部署所有服務 | `make k8s-apply` |
| 重建 image 並套用 | `make image-build-api && make k8s-restart-api` |
| 執行單元測試 | `make ci-test-unit` |
| 執行 Modulith 邊界驗證 | `make ci-test-modulith` |
| 查看服務狀態 | `kubectl get pods -n yocat-office-backend-local` |
| 查看 API log | `kubectl logs -f deploy/yocat-office-backend-api -n yocat-office-backend-local` |
| 重設環境（硬） | `make k8s-clean && make k8s-init && make k8s-apply` |
| 重設資料庫 | `make db-reset && ./scripts/bootstrap-secrets.sh` |
| 模擬 CI Pipeline | `make ci-dry-run` |
| Port-forward API | `make pf-api`（停止：`make pf-stop`） |
| K8s 互動 UI | `k9s -n yocat-office-backend-local` |

### 3.2 Dev-Tools 指令（Gitea / ArgoCD）

| 目的 | 指令 |
|------|------|
| 查看 Gitea（CI runner host） | `kubectl get pods -n dev-tools` |
| 查看 CI runner 狀態 | `kubectl get pods -n yocat-office-backend-ci` |
| Port-forward Gitea | `kubectl port-forward svc/gitea -n dev-tools 3000:3000` → http://localhost:3000 |
| Port-forward ArgoCD | `kubectl port-forward svc/argocd-server -n argocd 8443:443` → https://localhost:8443 |
| 進入 Gitea（本機 repo） | http://localhost:3000/dev/yocat-office-backend.git |
| 進入 ArgoCD | https://localhost:8443 |

---

## §4 本地環境常見問題（FAQ）

### Q: Pod 一直 Pending，怎麼處理？

```bash
# 查看 Event
kubectl describe pod <pod-name> -n yocat-office-backend-local

# 常見原因：資源不足
kubectl top nodes
# 若記憶體不足：Rancher Desktop → Preferences → Virtual Machine → Hardware → 增加 Memory（見 §5.2）
```

### Q: 所有 namespace 都要加 `-local` suffix 嗎？

是的（僅限應用 namespace）。本地環境規定：**應用 namespace 加 `-local`**（本專案 K8S_NAMESPACE = `yocat-office-backend`，故本地為 `yocat-office-backend-local`）；**CI runner namespace 固定為 `yocat-office-backend-ci`（不加 `-local`）**；**dev-tools namespace 固定為 `dev-tools`**；**ArgoCD 固定 `argocd`**；既有 PostgreSQL 在 `yocat` namespace。這樣可避免與 staging/production（`yocat-staging` / `yocat-prod`，EDD §3.4）衝突。

### Q: 如何確認 DB Migration 已執行？（Flyway）

```bash
# migration-tool = Flyway（SCHEMA.md §8）
# 方式 1：Flyway info（在 API Pod 內執行）
kubectl exec -it deploy/yocat-office-backend-api -n yocat-office-backend-local \
  -- flyway -url=jdbc:postgresql://postgres.yocat.svc.cluster.local:5432/yocat info

# 方式 2：直接查 Flyway history table（平台底座 schema，SCHEMA.md §8）
kubectl exec -it sts/postgres -n yocat \
  -- psql -U yocat -d yocat -c "SELECT * FROM platform.schema_migrations ORDER BY installed_on DESC LIMIT 5;"

# 方式 3：make 包裝
make db-status
```

> **連線值來源**：DB url / user / db 取自 LOCAL_DEPLOY.md §外部存取與 bootstrap-secrets（user=`yocat` / db=`yocat`）；正式環境值見 EDD §3.5。

### Q: Secret 過期或遺失怎麼辦？

```bash
# 重新執行 Secret Bootstrap（冪等；詳見 LOCAL_DEPLOY.md §3.5）
./scripts/bootstrap-secrets.sh

# 驗證 Secret 已建立
kubectl get secret yocat-pg -n yocat-office-backend-local

# 若需重建：k8s-clean / db-reset 後一律重跑 bootstrap（否則 secrets.env 遺失）
make k8s-clean && make k8s-init && ./scripts/bootstrap-secrets.sh
```

---

## §5 環境維護

### 5.1 定期維護項目

| 頻率 | 維護項目 | 指令 |
|------|---------|------|
| 每天 | 確認所有 Pod 就緒 | `kubectl get pods -n yocat-office-backend-local` |
| 每週 | 清理舊 image | `nerdctl --namespace k8s.io image prune -f` |
| 每月 | 更新 Helm chart（dev-tools/ArgoCD） | `helm repo update` |
| 每次 pull | 同步 DB Migration | `make db-migrate` |
| 每次 pull | 重跑 Modulith 邊界驗證 | `make ci-test-modulith` |

### 5.2 Rancher Desktop 資源建議

> 來源：LOCAL_DEPLOY.md §1 Prerequisites（至少 8 GB RAM / 4 CPU，承載 API 2 副本 + PostgreSQL + Redis）。

| 資源 | 最低配置 | 建議配置 |
|------|---------|---------|
| CPU | 4 cores | 8 cores |
| Memory | 8 GB | 16 GB |
| Disk | 40 GB | 80 GB |

設定路徑：Rancher Desktop → Preferences → Virtual Machine → Hardware；Container Engine 選 containerd（nerdctl）；Kubernetes 版本 1.30。

### 5.3 升級 Dev-Tools

```bash
helm repo update
# Gitea + act_runner（dev-tools / CI runner，CICD.md §6.1 / §8）
helm upgrade --install gitea gitea-charts/gitea -n dev-tools -f k8s/dev-tools/gitea-values.yaml
# 升級 chart repo 與 -f values 路徑必須與初次安裝一致（CICD.md §9）
```

---

## §6 文件邊界說明

| 問題 | 查找文件 |
|------|---------|
| 初次環境建置（安裝 k8s/DB/Redis、Secret Bootstrap） | [LOCAL_DEPLOY.md](LOCAL_DEPLOY.md) |
| CI/CD Pipeline 架構（GitHub/Gitea Actions、ArgoCD GitOps） | [CICD.md](CICD.md) |
| 生產環境 incident 處理（告警、回滾、對帳稽核） | [runbook.md](runbook.md) |
| API 規格（16 端點、request/response） | [API.md](API.md) |
| DB Schema 設計（8 表、schema-per-BC、Flyway） | [SCHEMA.md](SCHEMA.md) |
| 工程設計（7 BC、Clean Architecture、HC-1~HC-5、ADR） | [EDD.md](EDD.md) |
| 架構決策與拓撲（單體 HA ↔ 微服務） | [ARCH.md](ARCH.md) |
| 全域常數 / 列舉 / 錯誤碼（SSOT） | [CONSTANTS.md](CONSTANTS.md) |
| 日常開發操作（本文件） | DEVELOPER_GUIDE.md |
| 環境無法啟動 / Pod 異常 | DEVELOPER_GUIDE.md §4 FAQ |
| CI Pipeline 失敗 | DEVELOPER_GUIDE.md §2 |

---

## §7 Clean Architecture 分層說明

本專案採用 Clean Architecture 四層設計（詳見 EDD §3.1b）。新進工程師在寫任何代碼前，請先確認所在層次。

### 依賴方向（Dependency Rule）

```
Presentation → Application → Domain ← Infrastructure
```

**核心原則**：依賴方向只能由外向內。Domain 層不得引用 Infrastructure 具體類別，Infrastructure 反向實作 Domain 定義的介面。

### 各層職責與 Import 規則

| 層次 | Stereotype | 代表 Class（本系統） | 可以 import | 禁止 import |
|------|-----------|---------------------|------------|------------|
| Domain | AggregateRoot / Entity / ValueObject / DomainEvent / Repository(interface) | `Wallet`、`LedgerEntry`、`Account`、`Session`、`Money`、`AccountId`、`WalletRepository`、`MemberRepository`、`WalletBalanceChanged`、`MemberRegistered` | 同層 class、Java 21 標準庫 | ORM Entity、JDBC（`java.sql.*`）、HTTP Client、Spring（`org.springframework.*`）、外部 SDK |
| Application | UseCase / ApplicationService / Port(interface) | `SpendWalletUseCase`、`TopUpWalletUseCase`、`BuyEntitlementUseCase`、`RegisterMemberUseCase`、`WalletService`、`DomainEventPublisher` | Domain 層介面、DTO | `RepositoryImpl`、Adapter、ORM；不得 `new` 任何 Infrastructure 具體類別 |
| Infrastructure | RepositoryImpl / Adapter | `JdbcWalletRepository`、`ModulithEventPublisher`、`ScryptPasswordEncoder`、`BearerTokenFilter` | Domain 介面（實作用）、JDBC、DB Driver、第三方 SDK、Spring | 不得反向依賴 Application UseCase |
| Presentation | Controller / RequestDTO / ResponseDTO | `WalletController`、`AuthController` | Application UseCase / Service | Domain Entity（直接回傳）、Infrastructure |

> Class 名稱來源：[docs/diagrams/class-inventory.md](diagrams/class-inventory.md)（提取自 EDD §4.5.2）。

### SOLID 快速對照（本系統）

| 原則 | 本系統實作方式 |
|------|--------------|
| SRP（Single Responsibility） | `WalletService` 只處理錢包加值/扣款與帳本；`MemberService` 只處理註冊/登入/會話；`LeaderboardService` 只處理上榜/查榜。每個 Service 對應單一 BC 單一職責。 |
| OCP（Open / Closed） | 儲值包透過 `DepositPackCatalog`（介面）擴展：新增寶石包只需新增 `GemPackPolicy` 設定或實作，不改 `TopUpWalletUseCase`；新增破產救援策略只需實作 `RescueStrategy` 介面。 |
| LSP（Liskov Substitution） | `WalletRepository` 所有子型別（正式 `JdbcWalletRepository`、測試 `InMemoryWalletRepository`）可互換，`WalletService` 行為不變；合約測試保證替換後語意一致。 |
| ISP（Interface Segregation） | 讀寫分離：`WalletReadRepository`（`findBalance`）/ `WalletWriteRepository`（`debitWithLock`/`credit`）；`LedgerAppender` 僅暴露 `append(entry)`，不強迫實作無關方法。 |
| DIP（Dependency Inversion） | 高層 `SpendWalletUseCase` 依賴 Domain 的 `WalletRepository`、`DomainEventPublisher` 介面；具體 `JdbcWalletRepository`、`ModulithEventPublisher`（Infrastructure）由 Spring DI 於組態注入，Domain/Application 不 import 任何 JDBC/Spring 類別。 |

### 常見違規案例（FAQ）

**Q: 可以在 Domain 層 import Spring `@Repository` annotation 嗎？**
A: 不行。`@Repository` 是 Infrastructure 的技術細節；Domain 層只定義 `WalletRepository` interface，不引用框架 annotation。此類違規由 `make ci-archunit` 於 CI 攔截。

**Q: `SpendWalletUseCase` 可以直接 `new JdbcWalletRepository()` 嗎？**
A: 不行，違反 DIP。UseCase 只依賴 `WalletRepository` interface，具體實作由 Spring DI Container 建構子注入；`new` 任何 Infrastructure 類別一律禁止（EDD §3.1b 禁止清單）。

---

## §8 專案結構（Spring Modulith 7 BC 套件結構）

本系統為 **模組化單體（Modular Monolith）**：單一可部署單元 `yocat-office-backend-api`（Spring Boot 3.3，Port 8080）內含 7 個 Spring Modulith 模組，每個模組 = 一個 Bounded Context = 一個 `@ApplicationModule`。

### 8.1 目錄結構（Maven，base-package = `com.sayyogames.yocat`）

```
apps/api/src/main/java/com/sayyogames/yocat/
├── YocatOfficeApplication.java      # @SpringBootApplication + @Modulithic 入口
├── shared/                          # 橫切 SharedKernel：AccountId VO、Money、ApiError
│   ├── AccountId.java
│   └── Money.java
├── member/                          # BC 1：身分權威
│   ├── package-info.java            # @ApplicationModule(allowedDependencies={})
│   ├── api/                         # Presentation：AuthController + @NamedInterface
│   ├── application/                 # RegisterMemberUseCase / MemberService
│   ├── domain/                      # Account(AR) / Session / MemberRepository(介面) / MemberRegistered(Event)
│   └── infra/                       # JdbcMemberRepository / ScryptPasswordEncoder / SessionStore(Redis)
├── wallet/                          # BC 2：伺服器權威錢包
│   ├── package-info.java            # @ApplicationModule(allowedDependencies={"member"})
│   ├── api/ application/ domain/ infra/
├── deposit/   entitlement/   record/   leaderboard/   gamesave/   # BC 3~7，同四層結構
└── platform/                        # 底座（非 BC）：SecurityConfig / RateLimitFilter / ObservabilityConfig / OutboxConfig
apps/api/src/test/java/com/sayyogames/yocat/
├── ModularityTests.java             # ApplicationModules.of(...).verify()（HC-1~HC-5）
└── <bc>/...ModuleTest.java          # @ApplicationModuleTest 各 BC 隔離測試
pom.xml  /  docker/api/Dockerfile（multi-stage）  /  k8s/
```

### 8.2 七個 Bounded Context（模組名、職責、權威資料表）

| # | 模組名（BC） | 職責 | 權威 Schema / Tables | 對外 Public Interface |
|---|------------|------|---------------------|----------------------|
| 1 | `member` | 身分權威：註冊/登入/登出/我的資料（scrypt + session） | `member`: `accounts`, `sessions` | REST `/auth/*`；`MemberService.verifySession()`；Event `MemberRegistered` |
| 2 | `wallet` | 伺服器權威錢包：topup / spend，append-only ledger | `wallet`: `wallets`, `wallet_ledger` | REST `/wallet/*`；`WalletService.debit()/credit()`；Event `WalletBalanceChanged` |
| 3 | `deposit` | 儲值接口：假付費走 ledger，Phase B 真金流 | `deposit`: `deposit_pack` | REST `/wallet/topup`（pack 解析）；Event `DepositConfirmed` |
| 4 | `entitlement` | 已購權益：原子扣款+授予 / 列出 | `entitlement`: `entitlements` | REST `/entitlements/*`；Event `EntitlementGranted` |
| 5 | `record` | 經營史記錄：破產/成就/傳承（append-only） | `record`: `records` | REST `/records/*`；Event `BusinessRecordAdded` |
| 6 | `leaderboard` | 全域排行：歸戶寫入 + 公開 Top N | `leaderboard`: `leaderboard` | REST `/leaderboard`；Event `RunScoreSubmitted` |
| 7 | `game-save` | 跨裝置存檔：/save GET / PUT（slot upsert） | `game_save`: `saves` | REST `/save` |

> **平台底座（`platform`，非 BC，橫切）**：承載 7 BC 的 Spring Modulith 單體、Clean Architecture 4 層、Day1 HA（≥2 replicas / HPA / PodDisruptionBudget）、Event Publication Registry（JDBC Outbox）。來源 [CONSTANTS.md](CONSTANTS.md) §12 / EDD §3.8。

### 8.3 模組依賴 DAG（HC-5）

跨 BC 依賴必須為有向無環圖（DAG），`member` 為葉節點（被依賴），最長鏈 `entitlement → wallet → member`。任何循環依賴為缺陷，由 `ApplicationModules.verify()` 於 CI 阻斷 merge。

---

## §9 如何新增 / 修改一個 BC 的功能（Clean Architecture 分層放哪）

以「在 `wallet` BC 新增一個查詢 ledger 歷史的功能 `GET /wallet/ledger`」為例，說明四層放置順序（**由內而外**）。

### 步驟 1：Domain 層（最先，純邏輯零技術依賴）

- 位置：`wallet/domain/`
- 若需要新的查詢行為，於 `WalletRepository`（或讀取介面 `WalletReadRepository`，ISP）新增方法簽章，例如 `List<LedgerEntry> findLedger(AccountId id, int limit)`。
- 新增/調整的 Entity / ValueObject（如 `LedgerEntry`、`Money`）保持零 import Spring / JDBC。
- **禁止**：在此層 import `org.springframework.*` 或 `java.sql.*`。

### 步驟 2：Application 層（UseCase 編排）

- 位置：`wallet/application/`
- 新增 `QueryLedgerUseCase`，建構子注入 Domain 的 `WalletReadRepository` 介面（DIP），不得 `new` 任何 Infrastructure 類別。
- 跨 BC 需求（如需 member display_name）只能呼叫對方 Public `@NamedInterface`（如 `MemberService`）或訂閱 Domain Event，**不得**直接存取他 BC 的 Repository / Table（HC-2）。

### 步驟 3：Infrastructure 層（實作 Domain 介面）

- 位置：`wallet/infra/`
- 在 `JdbcWalletRepository` 實作新方法 `findLedger(...)`，寫 SQL / 用 JDBC。此層可 import Spring、JDBC、DB Driver。
- 只能存取 **本 BC 自己的 schema**（`wallet`）；禁止跨 schema JOIN（HC-1）。

### 步驟 4：Presentation 層（對外 REST）

- 位置：`wallet/api/`
- 在 `WalletController` 新增 `@GetMapping("/wallet/ledger")`，注入 Application 的 `QueryLedgerUseCase`。
- 回傳 `ResponseDTO`（不得直接回傳 Domain Entity）；錯誤碼依 [CONSTANTS.md](CONSTANTS.md) §10（400/401/402/404/409/500）。
- 若此方法需供其他 BC 同步呼叫，於 `api/` 以 `@NamedInterface` 暴露介面（見 §10）。

### 步驟 5：測試與驗證（缺一不可）

```bash
make ci-test-unit          # UseCase / Domain 單元測試（S/E/B 各 ≥ 1）
make ci-test-modulith      # 確認未破壞 BC 邊界（HC-1~HC-5）
make ci-archunit           # 確認 Domain 無違規 import
```

> 1 Class → 1 src 檔 → 1 test 檔（class-inventory.md 規則）；每個 Public Method 至少 3 個測試案例（Success / Error / Boundary）。

---

## §10 跨模組通訊規範（HC-1~HC-5）

> **鐵律**：跨 BC 只走 **Public `@NamedInterface`（同步）** 或 **Domain Event（非同步，Outbox）**。**禁止**跨 BC 直接呼叫對方內部類別、禁止跨 BC 存取他人 schema / JOIN、禁止跨 BC DB-level FK。

### 10.1 Hard Constraint 清單（HC-1 ~ HC-5）

| 編號 | 名稱 | 規則 | CI 驗證 |
|------|------|------|---------|
| HC-1 | Schema Ownership | 每 BC 獨佔自己的 DB schema；無兩 BC 擁有同一張 table；移除跨 BC DB-level FK，改以 `account_id BIGINT`（application-layer ID）參照 | `make ci-test-schema-isolation` |
| HC-2 | Cross-BC Access | 跨 BC 僅透過 Public `@NamedInterface` 或 Domain Event；禁止跨 BC 直呼 Repository / 存取他人 table / JOIN | `make ci-archunit` / `make ci-test-modulith` |
| HC-3 | Event-Driven | 模組間非同步通訊走 Spring Modulith Event Publication Registry（JDBC Outbox，與業務寫入同一交易，at-least-once） | `make ci-test-modulith` |
| HC-4 | Shared State Isolation | 無共享可變狀態；Redis key 以 BC 短名為 namespace 前綴（`member:*` / `leaderboard:*` / `rate:*`），前綴不重疊；強一致資料走 DB | 檢視 EDD §21.3 Redis Key Namespace Table |
| HC-5 | Dependency DAG | 所有模組間依賴構成有向無環圖，無循環 | `ApplicationModules.verify()`（`make ci-test-modulith`） |

### 10.2 同步通訊：Public `@NamedInterface`

適用「需同交易原子性」的呼叫，例如 `entitlement` 購買時同交易扣款：

```java
// wallet/api/WalletService.java —— wallet BC 對外暴露的 Public NamedInterface
public interface WalletService {
    WalletView topUp(AccountId accountId, Currency currency, long amount, String reason, Map<String,Object> meta);
    WalletView debit(AccountId accountId, Currency currency, long amount, String reason, Map<String,Object> meta); // 餘額不足擲 InsufficientBalanceException
    WalletView findWallet(AccountId accountId);
}

// entitlement/application/BuyEntitlementUseCase.java —— 下游 BC 只依賴此介面，不碰 wallet 內部
walletService.debit(accountId, Currency.GEMS, price, "buy_" + kind, meta); // 同交易原子扣款（EDD §3.4）
```

- `entitlement` 的 `package-info.java` 須宣告 `@ApplicationModule(allowedDependencies={"wallet", "member"})`，否則 `verify()` 失敗。
- 禁止 `entitlement` 直接注入 `JdbcWalletRepository` 或讀 `wallet.wallets` table。

### 10.3 非同步通訊：Domain Event（Outbox）

適用「最終一致、解耦」的通知，例如註冊成功後 provision 錢包：

```java
// member/domain/MemberRegistered.java —— Domain Event（純資料，零框架依賴）
public record MemberRegistered(AccountId accountId, String displayName) {}

// member/application —— 發佈（Outbox 與業務寫入同一交易）
domainEventPublisher.publish(new MemberRegistered(account.id(), account.displayName()));

// wallet —— 訂閱（事件完成後 Outbox 標記 completion_date）
@ApplicationModuleListener
void on(MemberRegistered e) {
    walletService.provision(e.accountId()); // 建錢包 + 寫 reason='init' ledger（balance=0）
}
```

- 事件經 Spring Modulith Event Publication Registry（`spring.modulith.events.jdbc.enabled=true`）；delivery = at-least-once，重啟自動重投未完成事件（`completion_date IS NULL`）。
- 消費端須對 `event_id` 冪等去重（EDD §設計目標）。

### 10.4 跨 BC 身分參照（Conformist）

`wallet` / `entitlement` / `record` / `leaderboard` / `game-save` 皆以 `account_id BIGINT`（application-layer ID）參照 `member`，**不設跨 schema FK**。帳號刪除以 `MemberDeleted` 事件驅動各 BC 匿名化 / 級聯清理（EDD §3.4 / §6.3）。

---

## §11 把一個 BC 抽成獨立微服務（in-process event → broker，不改 Domain 碼）

本系統刻意保留「Day1 可分可合」：同一套 Domain / Application 程式碼，透過組態在 **拓撲 A（單體 HA，最小成本）↔ 拓撲 B（獨立微服務，最大擴展）** 之間切換，**Domain 模型零改**（ARCH §3.2.1 / EDD ADR-001）。

### 11.1 兩種拓撲對照

| 面向 | 拓撲 A（單體 HA，Day1） | 拓撲 B（獨立微服務，成長期可選） | 是否改 Domain 碼 |
|------|------------------------|--------------------------------|------------------|
| 部署單元 | 單一 `yocat-office-backend-api`（7 BC 同 JVM） | 每 BC 獨立 Container | 否 |
| 跨 BC 事件 | in-process `@ApplicationModuleListener`（Outbox 本地訂閱） | Outbox 橋接至 Message Broker（NATS / Kafka） | 否（Event class 不變） |
| 跨 BC 同步呼叫 | `@NamedInterface` in-JVM（同交易） | REST / gRPC + Saga 補償 | Application 編排層變更，Domain 不變 |
| DB | 共享 PostgreSQL 實例（schema-per-BC） | 每服務獨立 DB 實例 | 否（schema 已隔離） |

### 11.2 抽離步驟（以 `leaderboard` 為例，純讀寫、無同步跨 BC 扣款，最易拆）

1. **前置確認（已由架構保證）**：`leaderboard` 已獨佔 `leaderboard` schema（HC-1）、跨 BC 只靠 `account_id` + `RunScoreSubmitted` 事件（HC-2/HC-3）、依賴 DAG 無循環（HC-5）。這些是「可零改拆分」的前提。
2. **抽出模組**：將 `leaderboard/` 套件連同其四層搬到新 Gradle/Maven module，產生獨立 `leaderboard-service` 可部署單元；Domain / Application 程式碼**原封不動**。
3. **替換 Infrastructure 傳輸層**：把 `ModulithEventPublisher`（本地 Outbox 訂閱）換成 **Outbox → Broker 橋接器**（publish `RunScoreSubmitted` 到 NATS/Kafka topic）；消費端改為 broker subscriber。Event class（`RunScoreSubmitted`）不變。
4. **DB 遷移**：將 `leaderboard` schema 遷到獨立 DB 實例（schema 已隔離，無跨 BC FK，遷移順序單純）。
5. **接合點調整（MD-05）**：Service Discovery、Ingress 路由、ArgoCD Application 拆分、observability 重新佈線。
6. **同步跨 BC 扣款改 Saga（僅限 entitlement→wallet）**：若拆的是 `entitlement`，原「同交易呼叫 `WalletService.debit()`」須改為 Saga（預授權 → 扣款 → 授予，失敗補償），屬 Application 編排層變更，Domain 不變（ARCH §3.2.1）。

### 11.3 為什麼 Domain 不用改

Clean Architecture 讓 Domain / Application 只依賴介面（`WalletRepository`、`DomainEventPublisher`、`@NamedInterface`）；傳輸實作（in-process vs broker）全在 Infrastructure 層。切換拓撲 = 換 Infrastructure adapter + 打包方式，上層零改（EDD §3.1b DIP + ARCH §3.2.1）。

---

## §12 編碼規範

| 項目 | 規範 |
|------|------|
| 語言 / 版本 | Java 21（善用 record、sealed、pattern matching、Virtual Threads） |
| 格式化 | Spotless；提交前 `./mvnw -pl apps/api spotless:apply`，CI 以 `make ci-lint`（`spotless:check`）把關 |
| 套件命名 | `com.sayyogames.yocat.<bc>.<layer>`；layer ∈ {api, application, domain, infra} |
| 命名慣例 | Controller `*Controller`、UseCase `*UseCase`、Service `*Service`、Repository 介面 `*Repository`、實作 `Jdbc*Repository`、Adapter `*Adapter`、Event 用過去式（`MemberRegistered`、`WalletBalanceChanged`） |
| Domain 純淨 | `domain/` 禁止 import `org.springframework.*` / `java.sql.*` / ORM / HTTP / 外部 SDK（`make ci-archunit` 把關） |
| 依賴注入 | 一律建構子注入 Interface；禁止欄位注入、禁止 `new` Infrastructure 類別 |
| 常數 / 列舉 / 錯誤碼 | 一律引用 [CONSTANTS.md](CONSTANTS.md)（SSOT）；禁止自行定義未登記的量化值 / 列舉成員 / 錯誤碼 |
| 錢包一致性 | 扣款走 `SELECT ... FOR UPDATE` 行鎖 + 冪等鍵；`wallet.cash/gems >= 0` underflow guard；ledger append-only 不可改 |
| 靜態分析 | SpotBugs / Semgrep / Trivy（`make ci-spotbugs` / `ci-semgrep` / `ci-dependency-scan`）；High/Critical = 0 |

---

## §13 測試撰寫（ArchUnit / ApplicationModules.verify）

### 13.1 測試分層與指令

| 測試類型 | 指令 | 說明 |
|---------|------|------|
| 單元測試 | `make ci-test-unit`（`./mvnw -pl apps/api test` + JaCoCo check ≥80%） | JUnit 5 + Mockito，不需 K8s；每 Public Method ≥ 3 案例（S/E/B） |
| 整合測試 | `make ci-test-integration`（`./mvnw verify -Pintegration`） | REST-assured + Testcontainers（起真實 PostgreSQL / Redis） |
| Modulith 邊界 | `make ci-test-modulith`（`./mvnw test -Dtest=ModularityTests`） | `ApplicationModules.verify()` 驗 HC-1~HC-5 |
| 架構守護 | `make ci-archunit` | ArchUnit：Domain 不得 import spring/java.sql、分層依賴方向 |
| Schema 隔離 | `make ci-test-schema-isolation` | 驗各 BC 只存取自己的 schema（HC-1） |
| 合約測試 | `make ci-contract-test` | `WalletService.debit()` 等 Public API 合約（Pact） |

### 13.2 Spring Modulith 邊界驗證骨架

```java
// apps/api/src/test/java/com/sayyogames/yocat/ModularityTests.java
class ModularityTests {
    @Test
    void verify_modulith_module_structure() {
        ApplicationModules modules = ApplicationModules.of(YocatOfficeApplication.class);
        modules.verify(); // 違反 HC-1/HC-2 邊界或 HC-5 循環 → 擲 AssertionError，CI 阻斷 merge
    }
}
```

> `verify()` 擲 `AssertionError` 代表 HC-5 循環或 HC-1/HC-2 邊界違規。遇到時：檢查 `package-info.java` 的 `@ApplicationModule(allowedDependencies={...})` 是否遺漏聲明，或是否出現跨 BC 直呼內部類別。

### 13.3 BC 隔離測試（@ApplicationModuleTest）

```java
// 僅啟動單一 BC（如 wallet），驗證其可獨立運作（為拆微服務鋪路）
@ApplicationModuleTest
class WalletModuleTest { /* ... */ }
```

### 13.4 測試追蹤（RTM）

class-inventory.md 定義 1 Class → 1 src → 1 test 檔；TC-ID 前綴（如 `TC-UNIT-SPENDWALLET`）後接 `-S01`/`-E01`/`-B01`。新增 Class 時同步新增對應測試檔，並更新 test-plan.md §15.2 Unit Test RTM。

---

## §14 提交與 PR 流程

### 14.1 分支與提交

1. 從 `main` 開 feature branch：`git switch -c feat/wallet-ledger-query`。
2. 小步提交，commit message 用 Conventional Commits：`feat(wallet): add ledger history query`、`fix(member): ...`、`docs(...)`、`test(...)`。
3. 推送到 Gitea（本機 `dev-tools` ns）或 GitHub，觸發 CI。

### 14.2 PR Gate（S1~S6，提交 PR 前本機預跑）

```bash
make ci-lint && make ci-archunit                 # S1 lint + 架構守護
make ci-test-unit                                # S2 unit + JaCoCo ≥80%
make ci-test-integration                         # S3 integration（Testcontainers）
make ci-test-modulith && make ci-contract-test   # S4 Modulith 邊界 + 合約
make ci-spotbugs && make ci-semgrep && make ci-dependency-scan   # S5 靜態分析（High/Critical=0）
make ci-dry-run                                  # S6 完整 PR Gate 本地模擬（act / Gitea Actions）
```

### 14.3 合併與部署（CD）

- PR 通過 6 個 CI stage（PR Gate）+ Code Review 後 merge `main`。
- merge 後 `deploy.yml` 推送 GitOps manifest，**ArgoCD 自動同步** dev → staging → prod（Canary via GitOps）。
- 部署須維持 Day1 HA：API ≥ 2 replicas、DB Primary+Standby、Redis Sentinel；Prod 不得排程停機或縮副本至 1。

### 14.4 回滾

```bash
make ci-rollback        # kubectl rollout undo + git revert → ArgoCD 重新同步
```

---

## §15 常見陷阱（Pitfalls）

| 陷阱 | 後果 | 正確做法 |
|------|------|---------|
| 在 Domain 層 import Spring / JDBC | `make ci-archunit` 失敗、破壞可測性與可拆性 | Domain 只定義介面；技術細節放 `infra/`（§7） |
| 跨 BC 直接注入他人 Repository 或查他人 table | `make ci-test-modulith` / schema-isolation 失敗，架構腐化 | 走 Public `@NamedInterface` 或 Domain Event（HC-2，§10） |
| 跨 BC 加 DB-level FK | 違反 HC-1，日後無法拆微服務 | 以 `account_id BIGINT`（application-layer ID）參照，無跨 schema FK |
| `package-info.java` 漏宣告 `allowedDependencies` | `verify()` 擲 AssertionError 擋 merge | 明確宣告每個 BC 的 `@ApplicationModule(allowedDependencies={...})` |
| 模組間產生循環依賴 | 違反 HC-5，`verify()` 失敗 | 維持 DAG；`member` 為葉節點，用事件反轉依賴方向 |
| 錢包扣款未上行鎖 / 未冪等 | 併發對帳不一致（P0 護欄破線） | `SELECT ... FOR UPDATE` + 冪等鍵；ledger append-only（§12） |
| 把遊戲模擬數值（`MAX_WORKERS` 等）搬進後端 | 違反「算力留本地」原則 | 本地算力常數留 client；信任層只透過 `wallet_ledger.reason`（如 `rescue`）感知結果（CONSTANTS.md §1） |
| 自行定義錯誤碼 / 列舉成員 | 下游契約破裂、客戶端無法預期處理 | 一律引用 CONSTANTS.md SSOT（§9 列舉、§10 錯誤碼） |
| namespace 用錯（漏 `-local` 或誤用 `-local` 於 ci） | 誤操作到錯誤環境 | app = `yocat-office-backend-local`；CI = `yocat-office-backend-ci`；dev-tools = `dev-tools`（§4 FAQ） |
| `make k8s-clean` / `db-reset` 後忘了重跑 bootstrap | Secret 遺失、Pod 起不來 | 重設後一律 `./scripts/bootstrap-secrets.sh`（§1 場景 E） |
| 回傳 Domain Entity 給前端 | 洩漏內部模型、耦合 API 與 Domain | Presentation 層回傳 ResponseDTO（§7） |
