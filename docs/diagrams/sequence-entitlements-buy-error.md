---
diagram: sequence-entitlements-buy-error
uml-type: Sequence Diagram（循序圖 — POST /entitlements/buy Error Paths）
source: docs/EDD.md §4.5.4.4
generated: 2026-10-30T00:00:00Z
---

# Sequence Diagram — `POST /entitlements/buy` Error Paths

> 來源：EDD.md §4.5.4.4

```mermaid
sequenceDiagram
    participant Client as Client
    participant Ctrl as EntitlementController
    participant UC as BuyEntitlementUseCase
    participant WS as WalletService
    participant DB as PostgreSQL

    Client->>Ctrl: POST /entitlements/buy {kind, itemId, currency, amount}
    alt kind 非法或缺 itemId
        Ctrl-->>Client: 400
        Note over Ctrl,Client: ErrorDTO：{error:"項目不正確"}
    else 已擁有同一 (kind,itemId)
        UC->>DB: BEGIN ; SELECT owned → found
        UC->>DB: ROLLBACK
        Ctrl-->>Client: 409 Conflict
        Note over Ctrl,Client: ErrorDTO：{error:"已經擁有這個項目"}
    else 餘額不足
        UC->>WS: debit(...) → InsufficientBalanceException
        UC->>DB: ROLLBACK
        Ctrl-->>Client: 402
        Note over Ctrl,Client: ErrorDTO：{error:"餘額不足", wallet:{...}}
    end
```
