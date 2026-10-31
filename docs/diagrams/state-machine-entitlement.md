---
diagram: state-machine-entitlement
uml-type: State Machine Diagram（狀態機圖 — Entitlement 生命週期）
source: docs/EDD.md §4.5.6 (3)
generated: 2026-10-30T00:00:00Z
---

# State Machine Diagram — Entitlement 生命週期

> 來源：EDD.md §4.5.6（3）

```mermaid
stateDiagram-v2
    [*] --> Unowned : accountCreated [always] / none
    Unowned --> Owned : buy(kind,itemId) [not owned and (amount==0 or balance>=amount)] / debitAndGrant
    Unowned --> Unowned : buy(kind,itemId) [balance<amount] / return402_rollback
    Owned --> Owned : buy(kind,itemId) [already owned] / return409_noDup
    Owned --> Owned : bankruptcyRecycle [always] / keep（破產不奪走權益）
    note right of Owned : recycle 僅影響遊戲內狀態，不觸及 entitlement BC（PRD §5.3 AC-5）
```

**Idempotency Table（Entitlement）：**

| Trigger | From | Idempotent? | 重複觸發行為 |
|---------|------|------------|------------|
| buy(kind,itemId) | Owned | ✅ 是 | 回 409「已經擁有」，不重複扣款/授予 |
