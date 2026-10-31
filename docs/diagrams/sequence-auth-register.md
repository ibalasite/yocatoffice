---
diagram: sequence-auth-register
uml-type: Sequence Diagram（循序圖 — POST /auth/register Happy Path，含跨 BC 事件）
source: docs/EDD.md §4.5.4.5
generated: 2026-10-30T00:00:00Z
---

# Sequence Diagram — `POST /auth/register` Happy Path（含跨 BC 事件）

> 來源：EDD.md §4.5.4.5

```mermaid
sequenceDiagram
    participant Client as Client
    participant Ctrl as AuthController
    participant UC as RegisterMemberUseCase
    participant MR as MemberRepository
    participant DB as PostgreSQL
    participant Pub as DomainEventPublisher
    participant WL as wallet 模組（@ApplicationModuleListener）

    Client->>Ctrl: POST /auth/register {email, displayName, password}
    Note over Client,Ctrl: RequestDTO：email(required,format), displayName(required,1-24), password(required,≥6)
    Ctrl->>UC: execute(RegisterCommand{email, displayName, password})
    UC->>DB: BEGIN TRANSACTION
    UC->>MR: existsByEmail(email)
    MR->>DB: SELECT 1 FROM member.accounts WHERE email=$1
    DB-->>MR: none
    UC->>MR: save(Account{email, name, scryptHash})
    MR->>DB: INSERT member.accounts RETURNING id
    DB-->>MR: accountId=1001
    UC->>Pub: publish(MemberRegistered{accountId, email})
    Pub->>DB: INSERT event_publication（同交易）
    UC->>DB: COMMIT
    DB-->>WL: （提交後）投遞 MemberRegistered
    WL->>DB: INSERT wallet.wallets(1001,0,0) ; INSERT wallet.wallet_ledger(gems,0,'init',0)
    Ctrl-->>Client: 200 OK {token, account, wallet:{cash:0,gems:0}}
```
