---
diagram: cicd-pipeline-sequence
uml-type: Sequence Diagram（CI Pipeline 循序圖）
source: docs/CICD.md §1.3 Pipeline Stage + §2 ci.yml + §7 ArgoCD GitOps
generated: 2026-10-30T00:00:00Z
---

# CI Pipeline Sequence Diagram — yocat-office-backend

> 來源：docs/CICD.md §1.3 Pipeline Stage 總覽 + §2 GitHub Actions ci.yml + §7 ArgoCD GitOps
>
> 技術棧：GitHub Actions（雲端 PR Gate）/ Gitea Actions（本機 act_runner）跑同一份 `.github/workflows`；
> 影像以 Kaniko/Buildx 建置 + cosign 簽章；CD 由 ArgoCD GitOps（watch k8s/overlays）執行。

```mermaid
sequenceDiagram
    participant Dev as Developer
    participant Git as Git Repo（GitHub / 本機 Gitea）
    participant CI as CI Runner（GitHub Actions / Gitea act_runner）
    participant Reg as Registry（registry.sayyogames.internal）
    participant Overlay as Git（k8s/overlays）
    participant Argo as ArgoCD 2.12
    participant K8s as K8s Cluster（dev→staging→prod）

    Dev->>Git: git push / open PR
    Git->>CI: on pull_request / push（webhook trigger）
    CI->>CI: S1 Checkout（actions/checkout）
    CI->>CI: S2 lint + ArchUnit（make ci-lint, make ci-archunit）
    CI->>CI: S3 unit-test + JaCoCo（make ci-test-unit, 覆蓋率 >= 80%）
    CI->>CI: S4 integration-test（Testcontainers + Pact + Modulith verify）
    CI->>CI: S5 static-analysis（SpotBugs + Semgrep + Trivy fs 依賴掃描）
    CI->>Reg: S6 image-build（Kaniko/Buildx push image:git-sha8）
    CI->>Reg: cosign sign --yes（keyless OIDC, Fulcio + Rekor）
    alt PR Gate 全綠（S1-S6 PASS）
        CI-->>Git: status check success（lint/unit-test/integration-test/static-analysis/image-build）
        Note over Git: PR 可合併至 main
    else 任一 stage FAIL（Fail-Fast）
        CI-->>Git: status check failure（立即中止，不進入下一 stage）
    end

    Note over Git,K8s: 以下為 merge main 後 CD（deploy.yml + ArgoCD GitOps）
    Dev->>Git: PR merge to main
    Git->>CI: on push main → deploy.yml
    CI->>Overlay: bump k8s/overlays/* imageTag = git-sha8
    Argo->>Overlay: detect change（watch）
    Argo->>K8s: sync dev（yocat-dev, auto prune + selfHeal）
    Argo->>K8s: sync staging（yocat-staging, Blue-Green）
    Argo->>K8s: sync prod（yocat-prod, Canary, >=2 replicas + HPA 2-8 + PDB）
    K8s-->>Argo: sync + health status
    alt HA Verify + Smoke + k6 PASS
        Argo-->>Dev: deployment notification（Slack #deploy-yocat-office-backend）
    else 部署驗證 FAIL
        CI->>K8s: make ci-rollback（kubectl rollout undo，即時止血）
        Dev->>Overlay: git revert bump commit（GitOps 真相回復）
        Argo->>K8s: 自動同步回舊版
    end
```

## 技術說明

此循序圖以服務內部視角描述從 `git push` 到生產環境部署的完整 Pipeline。CI 段（S1-S6）為 PR Gate，在 GitHub-hosted runner 或本機 Gitea `act_runner` 上執行**同一份** `ci.yml`（Local-First）。影像建置一律用 Kaniko（k3s 禁 privileged DinD），並以 cosign keyless 簽章。CD 段採 GitOps：`deploy.yml` 只 bump overlays imageTag，實際 sync 由 ArgoCD watch Git 後執行，三環境以 auto / Blue-Green / Canary 分級晉升。

## 白話說明

工程師送出程式後，機器自動幫忙檢查格式、跑測試、打包並簽章；全部通過才准合併。合併後系統自動把新版本依序推上開發、測試、正式環境，出問題會自動退回舊版。
