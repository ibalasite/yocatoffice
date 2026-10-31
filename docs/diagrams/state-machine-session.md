---
diagram: state-machine-session
uml-type: State Machine Diagram（狀態機圖 — Member Session 生命週期）
source: docs/EDD.md §4.5.1 (UC2/UC3) + docs/SCHEMA.md sessions + docs/EDD.md §4.5.2 Session
generated: 2026-10-30T00:00:00Z
---

# State Machine Diagram — Member Session 生命週期

> 推斷來源：EDD.md §4.5.2（Session Entity，`isExpired(now)`）+ SCHEMA.md `sessions` + UC2/UC3

```mermaid
stateDiagram-v2
    [*] --> Active : login(email,password) [credentialsValid] / issueToken(ttl=30d)
    Active --> Active : getMe(token) [now<expiresAt] / returnPrincipal
    Active --> Expired : clockTick [now>=expiresAt] / markExpired
    Active --> Revoked : logout(token) [always] / deleteSession
    Expired --> [*] : cleanup [always] / purgeRow
    Revoked --> [*] : return200 [always] / idempotentOk
    note right of Active : TTL 30 天；BearerTokenFilter 每次請求驗 expiresAt
    note right of Revoked : logout 冪等：重複登出同樣回 200
```

**轉換說明：**

| From | To | Trigger [guard] / action | 備註 |
|------|----|--------------------------|------|
| (init) | Active | login [credentialsValid] / issueToken | 簽發 session，TTL 30 天 |
| Active | Expired | clockTick [now>=expiresAt] / markExpired | 過期自動失效，下次請求回 401 |
| Active | Revoked | logout [always] / deleteSession | 主動登出，冪等 |
