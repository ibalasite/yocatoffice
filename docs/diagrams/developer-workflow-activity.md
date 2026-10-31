---
diagram: developer-workflow-activity
uml-type: Activity Diagram（開發者日常工作流程活動圖）
source: docs/CICD.md §5.3 Pre-PR Checklist + §4 Shared Make Targets
generated: 2026-10-30T00:00:00Z
---

# Developer Daily Workflow Activity Diagram — yocat-office-backend

> 來源：docs/CICD.md §5.3 開發者 Pre-PR Checklist + §4 Shared Make Targets（CI ↔ Local 共用介面）
>
> 本機與 CI 共用同一組 `make ci-*` target，確保本機 dry-run 與雲端 PR Gate 行為一致。

```mermaid
flowchart TD
    Start(["開始：修改功能"]) --> Lint{"make ci-lint && make ci-archunit 通過？"}
    Lint -->|"否"| FixLint(["修復格式 / Clean Arch 依賴違規"])
    FixLint --> Lint
    Lint -->|"是"| Unit{"make ci-test-unit（JaCoCo >= 80%）通過？"}
    Unit -->|"否"| FixUnit(["修復單元測試 / 補覆蓋率"])
    FixUnit --> Unit
    Unit -->|"是"| Integ{"make ci-test-integration（Testcontainers）通過？"}
    Integ -->|"否"| FixInteg(["修復整合問題"])
    FixInteg --> Integ
    Integ -->|"是"| Modulith{"make ci-test-modulith && make ci-contract-test（Pact）通過？"}
    Modulith -->|"否"| FixMod(["修復模組邊界 / 合約"])
    FixMod --> Modulith
    Modulith -->|"是"| Scan{"make ci-spotbugs && ci-semgrep && ci-dependency-scan 0 High/Critical？"}
    Scan -->|"否"| FixScan(["修復靜態分析 / 依賴漏洞"])
    FixScan --> Scan
    Scan -->|"是"| DryRun{"make ci-dry-run（act 模擬完整 Pipeline）通過？"}
    DryRun -->|"否"| FixPipe(["修復 Pipeline / placeholder / 明文 credential"])
    FixPipe --> DryRun
    DryRun -->|"是"| CreatePR(["建立 PR"])
    CreatePR --> CIGate{"GitHub / Gitea Actions PR Gate 全綠？"}
    CIGate -->|"否"| FixCI(["依 CI log 修復"])
    FixCI --> CIGate
    CIGate -->|"是"| Review{"Code Review 核准？"}
    Review -->|"requested changes"| UpdateCode(["更新程式碼"])
    UpdateCode --> CIGate
    Review -->|"approved"| Merge(["合併到 main → deploy.yml + ArgoCD GitOps 自動部署"])
```

## 技術說明

此活動圖描述開發者完成一個功能從修改到合併的完整內循環。核心是本機先以 `make ci-dry-run`（`act`）跑**與 CI 完全相同**的 workflow，將失敗盡量左移到本機，避免浪費雲端 PR Gate。每個檢查點都有修復回圈，Pre-PR Checklist 的第 7-8 步（無裸 placeholder、無明文 credential）併入 dry-run gate。

## 白話說明

工程師改完程式後，在自己電腦上照同一套檢查一關一關跑過（格式、測試、合約、安全掃描、整條流水線模擬），都通過才開 PR；線上再自動檢查一次加同事核准，合併後系統自動部署。
