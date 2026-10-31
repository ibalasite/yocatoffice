---
diagram: sequence-wallet-spend
uml-type: Sequence Diagram（循序圖 — POST /wallet/spend Happy Path）
source: docs/EDD.md §4.5.4.1 + docs/API.md POST /wallet/spend
generated: 2026-10-30T00:00:00Z
---

# Sequence Diagram — `POST /wallet/spend` Happy Path

> 來源：EDD.md §4.5.4.1

```mermaid
sequenceDiagram
    participant Client as Client（遊戲客戶端）
    participant Ctrl as WalletController
    participant UC as SpendWalletUseCase
    participant Repo as WalletRepository（interface）
    participant DB as PostgreSQL
    participant Pub as DomainEventPublisher（Outbox）

    Client->>Ctrl: POST /wallet/spend {currency, amount, reason}（Bearer token）
    Note over Client,Ctrl: RequestDTO：currency:"cash"|"gems"(required), amount:long(required,≥1,≤1e7), reason:string(≤40)
    Ctrl->>UC: execute(SpendCommand{accountId, currency, amount, reason})
    UC->>DB: BEGIN TRANSACTION
    DB-->>UC: OK
    UC->>Repo: findForUpdate(accountId, currency)
    Note right of UC: timeout:1000ms, retry:0（行鎖不重試）
    Repo->>DB: SELECT cash FROM wallet.wallets WHERE account_id=$1 FOR UPDATE
    DB-->>Repo: balance=20000
    alt 餘額 >= amount（20000 >= 1250）
        UC->>Repo: save(wallet.debit) + appendLedger(delta=-1250, balance=18750)
        Repo->>DB: UPDATE wallet.wallets SET cash=cash-1250 ; INSERT wallet.wallet_ledger(...)
        DB-->>Repo: OK
        UC->>Pub: publish(WalletBalanceChanged{accountId, CASH, -1250, 18750})
        Pub->>DB: INSERT event_publication(...)（同交易）
        UC->>DB: COMMIT
        Ctrl-->>Client: 200 OK
        Note over Ctrl,Client: ResponseDTO：{ok:true, currency:"cash", balance:18750, wallet:{cash:18750,gems:310}}
    end
```
