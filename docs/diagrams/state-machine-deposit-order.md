---
diagram: state-machine-deposit-order
uml-type: State Machine Diagram（狀態機圖 — Deposit Order 生命週期，Phase B）
source: docs/SCHEMA.md deposit_order + docs/EDD.md §4.6 DepositConfirmed
generated: 2026-10-30T00:00:00Z
---

# State Machine Diagram — Deposit Order 生命週期（Phase B 金流）

> 推斷來源：SCHEMA.md `deposit_order.status` + EDD.md §4.6 `DepositConfirmed` 事件

```mermaid
stateDiagram-v2
    [*] --> Pending : checkout(packId) [idempotencyKey new] / createOrder
    Pending --> Pending : checkout(packId) [idempotencyKey replay] / returnExistingOrder
    Pending --> Confirmed : paymentWebhook(eventId) [signatureValid and not processed] / creditWalletAndEmit(DepositConfirmed)
    Pending --> Failed : paymentWebhook(eventId) [signatureInvalid] / rejectNoCredit
    Pending --> Expired : clockTick [now>=order.expiresAt] / markExpired
    Confirmed --> [*] : done [always] / archive
    Failed --> [*] : done [always] / archive
    Expired --> [*] : done [always] / archive
    note right of Pending : idempotency_key UK 防重複下單
    note right of Confirmed : provider_event_id UK 防 webhook 重放；confirmed 僅入帳一次
```

**轉換說明：**

| From | To | Trigger [guard] / action | 備註 |
|------|----|--------------------------|------|
| (init) | Pending | checkout [idempotencyKey new] / createOrder | 建立訂單 |
| Pending | Confirmed | paymentWebhook [signatureValid, not processed] / creditWallet + emit DepositConfirmed | 入帳（orderId dedup） |
| Pending | Failed | paymentWebhook [signatureInvalid] / rejectNoCredit | 驗簽失敗 |
| Pending | Expired | clockTick [timeout] / markExpired | 未付款逾時 |
