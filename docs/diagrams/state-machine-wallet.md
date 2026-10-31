---
diagram: state-machine-wallet
uml-type: State Machine Diagram（狀態機圖 — Wallet 餘額生命週期）
source: docs/EDD.md §4.5.6 (1)
generated: 2026-10-30T00:00:00Z
---

# State Machine Diagram — Wallet 餘額生命週期（每 currency）

> 來源：EDD.md §4.5.6（1）

```mermaid
stateDiagram-v2
    [*] --> Zero : register [always] / emitInitLedger
    Zero --> Positive : topUp(amount) [amount>=1] / appendLedger(+delta)
    Positive --> Positive : topUp(amount) [amount>=1] / appendLedger(+delta)
    Positive --> Positive : spend(amount) [balance>=amount] / appendLedger(-delta)
    Positive --> Zero : spend(amount) [balance==amount] / appendLedger(-delta)
    Zero --> Zero : spendRejected(amount) [balance<amount] / return402_noLedger
    Positive --> Positive : spendRejected(amount) [balance<amount] / return402_noLedger
    note right of Zero : 餘額恆 >= 0（underflow guard）；拒絕扣款不寫 ledger
```

**Invalid Transition Table（Wallet）：**

| From | To | Behavior | Log |
|------|----|----------|-----|
| Zero/Positive | Negative | 靜默拒絕（擲 InsufficientBalanceException → 402），交易回滾 | WARN |

**Idempotency Table（Wallet）：**

| Trigger | From | Idempotent? | 重複觸發行為 |
|---------|------|------------|------------|
| spend(amount) | Positive | ❌ 非冪等 | 每次都扣（業務預期）；併發以行鎖序列化 |
| topUp(pack) Phase B | any | ✅（冪等鍵 order_id） | 同 order_id 重放只入帳一次（Outbox dedup） |
