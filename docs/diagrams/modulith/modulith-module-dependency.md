---
diagram: modulith-module-dependency
uml-type: Spring Modulith 模組依賴關係圖（DAG）
source: docs/ARCH.md §4 服務邊界 + docs/EDD.md §3.4/§4.3/§4.6
generated: 2026-10-30T00:00:00Z
---

# Spring Modulith — 模組依賴關係圖（DAG）

> 來源：ARCH.md §4 服務邊界表 + 跨 BC 依賴圖 + EDD.md §4.6 Domain Events。
> 圖例：實線 = Public API 同步呼叫；虛線 = Domain Event 非同步投遞。member 為葉節點（被依賴），全圖無循環（HC-5 DAG 約束，CI `ApplicationModules.verify()` 阻斷循環）。

```mermaid
graph TD
    member["member\n身分權威：註冊/登入/會話"]
    wallet["wallet\n伺服器權威錢包 + 審計帳本"]
    deposit["deposit\n儲值包解析 + 入帳（Phase B 金流）"]
    entitlement["entitlement\n購買權益（原子扣款+授予）"]
    record["record\n經營史記錄與查詢"]
    leaderboard["leaderboard\n上榜 upsert + 公開 Top N"]
    gamesave["game-save\n跨裝置存檔 upsert/還原"]

    entitlement -->|"Public API: WalletService.debit()（同交易）"| wallet
    wallet -.->|"MemberRegistered（Domain Event）"| member
    deposit -.->|"DepositConfirmed → wallet（Domain Event）"| wallet
    entitlement -->|"Conformist: account_id"| member
    record -->|"Conformist: account_id"| member
    leaderboard -->|"Conformist: account_id + display_name"| member
    gamesave -->|"Conformist: account_id"| member
    wallet -->|"Conformist: account_id"| member
```

**DAG 驗證：**

| 驗證項目 | 結果 | 說明 |
|---------|------|------|
| 是否存在循環依賴 | NO | 無任何 A→…→A 路徑 |
| 依賴圖為 DAG | CONFIRMED | member 為葉節點，無反向邊 |
| 最長依賴鏈 | 3 層 | entitlement → wallet → member |
