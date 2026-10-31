---
diagram: sequence-entitlements-buy
uml-type: Sequence Diagram（循序圖 — POST /entitlements/buy Happy Path）
source: docs/EDD.md §4.5.4.3
generated: 2026-10-30T00:00:00Z
---

# Sequence Diagram — `POST /entitlements/buy` Happy Path（跨模組原子扣款+授予）

> 來源：EDD.md §4.5.4.3

```mermaid
sequenceDiagram
    participant Client as Client
    participant Ctrl as EntitlementController
    participant UC as BuyEntitlementUseCase
    participant WS as WalletService（wallet Public API）
    participant ER as EntitlementRepository
    participant DB as PostgreSQL

    Client->>Ctrl: POST /entitlements/buy {kind, itemId, currency, amount}
    Note over Client,Ctrl: RequestDTO：kind:"cosmetic"|"dream"|"scenario"(required), itemId:string(required), currency:string, amount:long(≥0)
    Ctrl->>UC: execute(BuyCommand{accountId, kind, itemId, currency, amount})
    UC->>DB: BEGIN TRANSACTION
    UC->>ER: existsOwned(accountId, kind, itemId)
    ER->>DB: SELECT 1 FROM entitlement.entitlements WHERE ...
    DB-->>ER: none
    alt 未擁有 且 (amount==0 或 餘額足)
        UC->>WS: debit(accountId, currency, amount, "buy_"+kind)（同交易）
        WS->>DB: SELECT ... FOR UPDATE ; UPDATE wallet ; INSERT ledger
        DB-->>WS: newBalance
        UC->>ER: grant(accountId, kind, itemId)
        ER->>DB: INSERT entitlement.entitlements(...)
        UC->>DB: COMMIT
        Ctrl-->>Client: 200 OK
        Note over Ctrl,Client: ResponseDTO：{ok:true, wallet:{...}, entitlements:[...]}
    end
```
