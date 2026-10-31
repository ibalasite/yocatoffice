---
diagram: cicd-pr-gate-activity
uml-type: Activity Diagram（PR Gate 活動圖）
source: docs/CICD.md §5 PR Gate + §2 ci.yml job names
generated: 2026-10-30T00:00:00Z
---

# PR Gate Activity Diagram — yocat-office-backend

> 來源：docs/CICD.md §5.1 PR Gate 清單 + §5.2 Branch Protection（required-status-checks）
>
> Branch Protection contexts（必須與 ci.yml job name 一致）：
> `lint` / `unit-test` / `integration-test` / `static-analysis` / `image-build`，Fail-Fast。

```mermaid
flowchart TD
    Start(["Developer: git push to PR branch"]) --> Checkout["S1 Checkout（actions/checkout）"]
    Checkout --> Lint{"S2 lint + ArchUnit（make ci-lint / ci-archunit）通過？"}
    Lint -->|"否（Clean Arch 依賴違規）"| Reject1(["PR BLOCKED — lint/ArchUnit failed"])
    Lint -->|"是"| Unit{"S3 unit-test + JaCoCo >= 80% 通過？"}
    Unit -->|"否（測試失敗或覆蓋率不足）"| Reject2(["PR BLOCKED — unit-test failed"])
    Unit -->|"是"| Integ{"S4 integration-test（Testcontainers + Pact + Modulith verify）通過？"}
    Integ -->|"否（合約/模組邊界違規）"| Reject3(["PR BLOCKED — integration-test failed"])
    Integ -->|"是"| Static{"S5 static-analysis（SpotBugs + Semgrep + 依賴掃描）0 High/Critical？"}
    Static -->|"否（發現 High/Critical）"| Reject4(["PR BLOCKED — static-analysis failed"])
    Static -->|"是"| Image{"S6 image-build（Kaniko + cosign sign + Trivy）0 CRITICAL？"}
    Image -->|"否（映像漏洞或建置失敗）"| Reject5(["PR BLOCKED — image-build failed"])
    Image -->|"是（docs-only PR 可 [skip-image]）"| Review{"Code Review 核准（>= 1 approval, dismiss stale）？"}
    Review -->|"否（requested changes）"| Update(["更新程式碼 → 重新觸發 CI"])
    Update --> Checkout
    Review -->|"是"| AllPass(["All required-status-checks PASSED"])
    AllPass --> Merge(["PR merge to main（required_linear_history）"])
```

## 技術說明

此活動圖對應 GitHub Branch Protection 的 5 個 required status checks。任一 Gate 失敗即 Fail-Fast 中止（不進入下一 stage），決策點兩個分支皆標注具體失敗條件。`image-build` 允許 docs-only PR 以 `[skip-image]` commit prefix 跳過。合併需 Review 核准且採 linear history。

## 白話說明

PR 要合併前必須依序通過五道自動檢查（格式、單元測試、整合測試、安全掃描、映像建置），任何一道沒過就擋下；全過加上一位同事核准才能併入主幹。
