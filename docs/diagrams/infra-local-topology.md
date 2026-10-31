---
diagram: infra-local-topology
uml-type: Deployment Diagram（本地基礎設施拓撲圖）
source: docs/CICD.md §8 Gitea + §6 Self-Hosted Runner + §7 ArgoCD + LOCAL_DEPLOY.md namespace
generated: 2026-10-30T00:00:00Z
---

# Local Infrastructure Topology — yocat-office-backend（Rancher Desktop k3s）

> 來源：docs/CICD.md §8.1 Gitea 架構 + §6 ARC Runner + §7 ArgoCD + docs/LOCAL_DEPLOY.md §2 namespace
>
> 本機閉環（Local-First）：Gitea（dev-tools）→ Gitea Actions / ARC runner（ci）→ ArgoCD（argocd）→ App（local）。

```mermaid
graph TB
    Dev["Developer（本機）"]

    subgraph RD["Rancher Desktop（k3s）"]
        subgraph DevTools["dev-tools namespace"]
            Gitea["Gitea + act_runner（gitea.dev-tools:3000, ClusterIP）"]
        end
        subgraph CI["yocat-office-backend-ci namespace"]
            Runner["self-hosted runner / ARC（runner:8080）"]
            Kaniko["Kaniko executor（ephemeral, 免 privileged）"]
        end
        subgraph ArgoNS["argocd namespace"]
            Argo["ArgoCD 2.12（watch overlays）"]
        end
        subgraph AppNS["yocat-office-backend-local namespace"]
            Nginx["nginx Ingress（:80, yocat-office-backend.local）"]
            API["API Pods（>=2 replicas, Spring Boot + Java 21）"]
            Redis["Redis（session store）"]
        end
        subgraph DataNS["yocat namespace"]
            DB["PostgreSQL（yocat-pg, 沿用既有 Secret）"]
        end
    end

    Reg["Container Registry（registry.sayyogames.internal）"]

    Dev -->|"git push（TCP:3000）"| Gitea
    Gitea -->|"Gitea Actions Webhook POST"| Runner
    Runner --> Kaniko
    Kaniko -->|"push image:git-sha8（+ cosign sign）"| Reg
    Runner -->|"bump k8s/overlays imageTag"| Gitea
    Argo -->|"watch repo（HTTP:3000）"| Gitea
    Argo -->|"sync（kubectl apply）"| API
    Reg -.->|"imagePull"| API
    Nginx -->|"HTTP:80"| API
    API -->|"TCP:6379"| Redis
    API -->|"TCP:5432（PostgreSQL Wire Protocol）"| DB
```

## 技術說明

此拓撲圖呈現 Rancher Desktop k3s 上的完整本機 CI/CD 閉環。四個工具 namespace（dev-tools / ci / argocd / app）加資料 namespace（yocat，沿用既有 `yocat-pg`）。本機 Gitea 取代外部 GitHub，Gitea Actions 以 `act_runner` 跑同一份 workflow；影像建置用 Kaniko（k3s 禁 privileged DinD）。CD 由 ArgoCD watch Gitea overlays 後 sync。生產/Staging 環境的 repoURL 改指向 `github.com/sayyogames/yocat-office-backend`。

## 白話說明

整套開發與部署系統都跑在開發者自己電腦的 k3s 上：本機 Git 伺服器、打包機、自動部署器、應用程式各自分區，不需連外部雲端就能完整走一遍上線流程。
