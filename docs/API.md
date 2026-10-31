# API — yoCat Office 後端信任層 API 設計文件

## Document Control

| 欄位 | 值 |
|------|-----|
| Document ID | `API-YOCAT-OFFICE-BACKEND-20261030` |
| Version | `1.0` |
| Status | Draft |
| Classification | Internal |
| Owner | Backend Lead / API Guild（聖祐遊戲） |
| **Owning Bounded Context / Service** | **yocat-office-backend**（Spring Modulith 模組化單體，單一對外發布服務；內含 7 個有界上下文：member / wallet / deposit / entitlement / record / leaderboard / game-save，對應 ARCH §4 / EDD §3.4） |
| Created | 20261030 |
| Last Updated | 20261030 |
| Upstream EDD | [docs/EDD.md](EDD.md) |
| Upstream PRD | [docs/PRD.md](PRD.md) |
| Upstream CONSTANTS（SSOT） | [docs/CONSTANTS.md](CONSTANTS.md) |
| OpenAPI Spec | [docs/openapi.yaml](openapi.yaml)（見 §12） |
| Changelog | [見 §13 Changelog](#13-api-changelog) |

## Change Log

| Version | Date | Author | Summary |
|---------|------|--------|---------|
| 1.0 | 20261030 | gendoc gen API | 初稿：依 EDD §5.2 Endpoint Inventory + CONSTANTS SSOT 生成 16 個業務端點（+2 健康檢查），涵蓋 7 有界上下文；Java 21 / Spring Boot 3.3 / Spring Modulith DTO |

---

## 0. 快速概覽

| 項目 | 值 |
|------|-----|
| Base URL（Production） | `https://api.yocat.sayyogames.com` |
| Base URL（Staging） | `https://api-staging.yocat.sayyogames.com` |
| Base URL（Development） | `http://localhost:8080` |
| 協議 | HTTPS 強制（Production / Staging；本地 HTTP） |
| API 版本 | v1（隱含，路徑即 `/auth/*`、`/wallet/*` 等，與原型一致；破壞性變更時引入 `/v2` 前綴） |
| 認證方式 | `Authorization: Bearer <session_token>`（opaque session token，32 bytes hex）；公開端點除外 |
| 資料格式 | `application/json; charset=utf-8` |
| 時區 | 所有時間欄位為 UTC，ISO 8601 格式（`2026-10-30T00:00:00Z`） |
| 分頁策略 | 列表端點採 Cursor-based；`GET /records`、`GET /leaderboard` 以時間/分數排序 + `limit` 夾擠（詳見 §2.5） |
| 技術棧 | Java 21 + Spring Boot 3.3 + Spring Modulith 1.3（Clean Architecture 4 層 + SOLID） |

**公開端點（不需 token）：** `POST /auth/register`、`POST /auth/login`、`GET /leaderboard`、`GET /deposit/packs`、`GET /health/live`、`GET /health/ready`。其餘端點一律需 Bearer session token。

**設計基準來源：** 本文件端點、路徑、Request/Response、錯誤碼均以 [EDD §5.2 Endpoint Inventory](EDD.md) 與 [CONSTANTS（SSOT）](CONSTANTS.md) 為準，不自行新增未登記之常數、列舉或錯誤碼。

---

## 1. 概述

**上下游一致性聲明：**

```
本文件的 API 序列圖描述 Client ↔ API 層的 HTTP 請求-回應視角。
同一業務操作的服務內部詳細流程，見 docs/EDD.md §4.5.4。
若兩份文件描述同一操作，以下優先級解決矛盾：
  1. 若 Client 操作無效（400/401），以本文件回應規格為準
  2. 若 Server 內部流程有異，以 EDD 為準
  3. 若兩者皆不明確，標記 [UPSTREAM_CONFLICT] 並提交 ADR
```

### 1.1 設計原則

- **RESTful 資源導向**：URL 使用名詞（`/entitlements`、`/records`、`/leaderboard`）；動作語意以 HTTP method 表達。
- **伺服器權威（Server-Authoritative）**：錢包餘額（cash / gems）、權益、記錄、排行分數一律由伺服器當權威來源，client 只讀顯示；任何餘額變動必寫 append-only `wallet_ledger`（CONSTANTS §9 reason 列舉）。
- **冪等性**：`POST /auth/logout`（by token）、`PUT /save`（by `account_id,slot`）、`POST /leaderboard`（by `run_id` upsert）、`POST /entitlements/buy`（by `UNIQUE(account_id,kind,item_id)`）、`POST /deposit/checkout`（by `orderId` / `Idempotency-Key`）皆為冪等。
- **Service Encapsulation（Spring Modulith HC-2）**：本服務為 7 個 BC 的唯一對外介面；BC 之間不得直接存取彼此 DB Schema，跨 BC 僅透過 Public API（如 `WalletService.debit()`）或 Domain Event（Transactional Outbox）互動（EDD §3.4 / §4.6）。API Response 欄位名稱經 DTO/View Model 層與 DB 欄位解耦（DB 欄位重命名不影響 API Contract）。
- **向後相容**：新欄位以 additive 方式加入，不移除或重命名現有欄位（破壞性變更升主版本）。

### 1.2 Bounded Context 歸屬（HC-2）

> 本 API 文件覆蓋 yocat-office-backend 單一 Spring Modulith 服務內的 7 個有界上下文。每個 Endpoint 只屬於一個 BC；BC 邊界與 §3 的 BC 分組標題一致；Owned Tables 與 ARCH §4.0 / EDD §3.4 Schema 擁有權表一致。跨 BC 呼叫走 Public API 或 Domain Event。

| Bounded Context | Path Prefix | Owning Team | Owned DB Schema（對應 SCHEMA / EDD §3.4） |
|-----------------|------------|------------|-------------------------------------------|
| member | `/auth/` | Backend Core | `member`: `accounts`, `sessions` |
| wallet | `/wallet/` | Backend Core | `wallet`: `wallets`, `wallet_ledger` |
| deposit | `/deposit/`、`POST /wallet/topup`（pack 解析） | Backend Core | `deposit`: `deposit_pack`（目錄）、`deposit_order`（Phase B） |
| entitlement | `/entitlements` | Backend Core | `entitlement`: `entitlements` |
| record | `/records` | Backend Core | `record`: `records` |
| leaderboard | `/leaderboard` | Backend Core | `leaderboard`: `leaderboard` |
| game-save | `/save` | Backend Core | `game_save`: `saves` |
| 平台底座（非 BC，橫切） | `/health/` | Platform | —（無擁有表；承載 7 BC + Outbox） |

### 1.3 API 序列圖（Client 視角）

> 覆蓋規則：每個 Mutation Endpoint（POST/PUT）至少一張，含 Happy Path + ≥2 Error Path。本系統 Mutation 共 10 個，依業務域合併呈現關鍵流程；其餘同模式者於圖下對照表標注。

**§1.3.1 — `POST /auth/register`（含跨 BC 事件 MemberRegistered）**

```mermaid
sequenceDiagram
    participant C as Client
    participant GW as API (Spring Security Filter)
    participant M as MemberService
    participant R as MemberRepository
    participant DB as PostgreSQL
    participant OB as Outbox (event_publication)
    participant W as WalletService

    C->>GW: POST /auth/register {email, displayName, password}
    GW->>M: register(cmd)
    alt email 格式/名稱/密碼不合法 (fail-fast)
        M-->>C: 400 {error:"email 格式不正確"}
    else email 已存在
        M->>R: SELECT 1 FROM accounts WHERE email=?
        R-->>M: 命中
        M-->>C: 409 {error:"這個 email 已經註冊過了"}
    else Happy Path
        M->>DB: BEGIN TX
        M->>DB: INSERT accounts(email,display_name,pass_hash)
        M->>OB: INSERT event_publication(MemberRegistered)（同交易）
        M->>DB: COMMIT
        M->>M: newSession() → 簽發 session (TTL 30 天)
        M-->>C: 200 {token, account, wallet:{cash:0,gems:0}}
        par [async] Outbox 投遞
            OB-->>W: deliver MemberRegistered {accountId,email}
            W->>DB: INSERT wallets(0,0) + wallet_ledger(reason=init,balance=0)
        end
    end
```

**§1.3.2 — `POST /wallet/spend`（行鎖扣款，Happy + 3 Error Path）**

```mermaid
sequenceDiagram
    participant C as Client
    participant GW as API (BearerTokenFilter)
    participant WS as WalletService
    participant DB as PostgreSQL
    participant OB as Outbox

    C->>GW: POST /wallet/spend {currency, amount, reason} (Bearer)
    alt 認證失敗（無/過期 token）
        GW-->>C: 401 {error:"請先登入"}
    else amount ≤ 0
        GW->>WS: debit(cmd)
        WS-->>C: 400 {error:"金額不正確"}
    else DB/連線不可用（Circuit Breaker Open）
        WS--xDB: SELECT ... FOR UPDATE（逾時）
        WS-->>C: 503 {error:"服務暫時不可用"} + Retry-After
    else Happy Path
        WS->>DB: BEGIN TX
        WS->>DB: SELECT cash/gems FROM wallets WHERE account_id=? FOR UPDATE
        alt 餘額 < amount
            WS->>DB: ROLLBACK
            WS-->>C: 402 {error:"餘額不足", wallet}
        else 餘額足夠
            WS->>DB: UPDATE wallets SET col = col - amount
            WS->>DB: INSERT wallet_ledger(delta=-amount, reason, balance)
            WS->>OB: INSERT event_publication(WalletBalanceChanged)
            WS->>DB: COMMIT
            WS-->>C: 200 {ok:true, currency, balance, wallet}
            par [async]
                OB-->>OB: deliver WalletBalanceChanged
            end
        end
    end
```

**§1.3.3 — `POST /entitlements/buy`（原子扣款 + 授予，Happy + Error）**

```mermaid
sequenceDiagram
    participant C as Client
    participant GW as API (BearerTokenFilter)
    participant ES as EntitlementService
    participant WS as WalletService
    participant DB as PostgreSQL
    participant OB as Outbox

    C->>GW: POST /entitlements/buy {kind,itemId,currency,amount} (Bearer)
    alt kind 非法 / itemId 空
        ES-->>C: 400 {error:"項目不正確"}
    else 已擁有 (UNIQUE 命中)
        ES->>DB: SELECT 1 FROM entitlements WHERE account_id,kind,item_id
        ES-->>C: 409 {error:"已經擁有這個項目"}
    else Happy Path (amount>0)
        ES->>DB: BEGIN TX
        ES->>WS: WalletService.debit(account,currency,amount) [同交易]
        alt 餘額不足
            WS->>DB: ROLLBACK
            WS-->>C: 402 {error:"餘額不足", wallet}
        else
            WS->>DB: UPDATE wallets - amount + INSERT ledger(reason=buy_<kind>)
            ES->>DB: INSERT entitlements(account,kind,item_id)
            ES->>OB: INSERT event_publication(EntitlementGranted)
            ES->>DB: COMMIT
            ES-->>C: 200 {ok:true, wallet, entitlements}
            par [async]
                OB-->>OB: deliver EntitlementGranted
            end
        end
    end
```

**§1.3.4 — `POST /deposit/checkout`（Phase A 假付費 / Phase B 金流，冪等 orderId）**

```mermaid
sequenceDiagram
    participant C as Client
    participant GW as API
    participant DS as DepositService
    participant DB as PostgreSQL
    participant OB as Outbox
    participant W as WalletService

    C->>GW: POST /deposit/checkout {packId} + Idempotency-Key (Bearer)
    alt packId 不在目錄
        DS-->>C: 400 {error:"儲值包不正確"}
    else Idempotency-Key 重放（已確認）
        DS->>DB: SELECT deposit_order WHERE idempotency_key=?
        DS-->>C: 200 {orderId, status:"confirmed"}（回放原結果）
    else Happy Path（Phase A 假付費）
        DS->>DB: BEGIN TX
        DS->>DB: INSERT deposit_order(status=confirmed, idempotency_key)
        DS->>OB: INSERT event_publication(DepositConfirmed)
        DS->>DB: COMMIT
        DS-->>C: 200 {orderId, packId, status:"confirmed"}
        par [async] credit wallet
            OB-->>W: deliver DepositConfirmed {accountId,orderId,currency,amount}
            loop [retry 最多 5 次，指數退避]
                W->>DB: credit wallet + INSERT ledger(reason=topup) [event_id dedup]
            end
        end
    end
```

**§1.3.5 — `PUT /save`（upsert，冪等）+ `POST /records` + `POST /leaderboard`（簡單寫入）**

```mermaid
sequenceDiagram
    participant C as Client
    participant GW as API (BearerTokenFilter)
    participant S as SaveService / RecordService / LeaderboardService
    participant DB as PostgreSQL

    C->>GW: PUT /save {slot?, data} (Bearer)
    alt 未認證
        GW-->>C: 401 {error:"請先登入"}
    else data 缺失（PUT /save）
        S-->>C: 400 {error:"缺 data"}
    else Happy Path
        S->>DB: INSERT ... ON CONFLICT(account_id,slot|run_id) DO UPDATE（upsert，冪等）
        S-->>C: 200 {ok:true}
    end
```

> **Mutation 序列對照：** `POST /auth/login`（同 §1.3.1 不含事件，錯誤 401 帳密錯）；`POST /auth/logout`（冪等刪 session，恆 200）；`POST /wallet/topup`（同 §1.3.2 credit 模式，無餘額檢查，400 金額不正確）；`POST /records`、`POST /leaderboard` 見 §1.3.5。

---

## 2. 通用規範

### 2.1 Standard Request Headers

| 標頭 | 格式 | 必填 | 說明 |
|------|------|------|------|
| `Authorization` | `Bearer <session_token>` | 是（公開端點除外） | opaque session token（32 bytes hex，TTL 30 天） |
| `Content-Type` | `application/json` | POST/PUT | 請求 body 格式 |
| `Accept` | `application/json` | 建議 | 可接受的回應格式 |
| `X-Request-Id` | UUID v4 / traceparent | 建議 | 追蹤用唯一請求 ID；未提供時伺服器自動產生 |
| `Idempotency-Key` | UUID v4 | `POST /deposit/checkout` 必填 | 冪等鍵；伺服器保存 24 小時（§7） |

**範例：**
```http
POST /wallet/spend HTTP/1.1
Host: api.yocat.sayyogames.com
Authorization: Bearer 3f8c1d2ae9b74c0a1b2c3d4e5f60718293a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8
Content-Type: application/json
Accept: application/json
X-Request-Id: 550e8400-e29b-41d4-a716-446655440000
```

### 2.2 Standard Response Headers

| 標頭 | 格式 | 說明 |
|------|------|------|
| `Content-Type` | `application/json; charset=utf-8` | 回應格式 |
| `X-Request-Id` | UUID v4 | Echo 回請求的 Request ID（或伺服器產生） |
| `X-RateLimit-Limit` | integer | 當前時間窗口的請求上限 |
| `X-RateLimit-Remaining` | integer | 當前時間窗口的剩餘請求次數 |
| `X-RateLimit-Reset` | Unix timestamp（秒） | 速率限制重置時間點 |
| `Retry-After` | integer（秒） | 僅 429 / 503 回應附帶 |

**CORS（EDD §5.3）：** `Access-Control-Allow-Origin`（Phase A `*`，正式收斂白名單）、`Access-Control-Allow-Methods: GET,POST,PUT,OPTIONS`、`Access-Control-Allow-Headers: Content-Type,Authorization`；`OPTIONS` 預檢回 `204 No Content`。

### 2.3 Standard Response Body

> 本 API 為 api-only 契約，沿用原型 wire 格式（EDD §5.1）：成功回傳業務物件本體（非 `data` 包裝），錯誤統一為 `{ "error": "<zh-TW 訊息>" }`。錢包相關 402 另附 `wallet`。

**成功（範例，`POST /wallet/spend`）：**
```json
{
  "ok": true,
  "currency": "cash",
  "balance": 18750,
  "wallet": { "cash": 18750, "gems": 330 }
}
```

**失敗（統一錯誤格式）：**
```json
{ "error": "餘額不足", "wallet": { "cash": 500, "gems": 0 } }
```

### 2.4 HTTP Status Code（本系統實際使用集合，CONSTANTS §10）

| 情境 | Status | 說明 |
|------|--------|------|
| 查詢/操作成功 | 200 OK | 回傳對應資源 |
| CORS 預檢 | 204 No Content | `OPTIONS` 回應 |
| 參數錯誤 | 400 Bad Request | email 格式、名稱越界、密碼過短、金額不正確、kind/type 非法、缺 data/runId |
| 未授權 | 401 Unauthorized | 無/過期/無效 token；登入帳密錯 |
| 需付費（餘額不足） | 402 Payment Required | spend / buy 餘額 < amount（附 `wallet`） |
| 找不到 | 404 Not Found | 命中前綴但無對應路由 |
| 衝突 | 409 Conflict | email 已註冊；已擁有 `(kind,item_id)` |
| 速率超限 | 429 Too Many Requests | 含 `Retry-After`（§6） |
| 伺服器錯誤 | 500 Internal Server Error | 未預期例外（交易回滾，不產生半筆異動） |
| 服務不可用 | 503 Service Unavailable | 依賴（DB/Redis）不可用；含 `Retry-After`（§14 HA） |

### 2.5 Pagination — 列表查詢策略

本系統列表端點資料量受控（單帳號記錄、排行 Top N），採**排序 + `limit` 夾擠**策略，避免全表掃描；不回傳 `total_count`（大資料集避免 COUNT(*)）。

| 端點 | 排序 | limit 規則 | 游標欄位（Phase B 擴充） |
|------|------|-----------|------------------------|
| `GET /records` | `created_at DESC` | 固定上限 100 筆（CONSTANTS §7） | `created_at` + `id`（base64 cursor） |
| `GET /leaderboard` | `score DESC` | 預設 20，夾擠 `[1,100]`（CONSTANTS §3） | `score` + `run_id` |

> Phase B 若需無限捲動，`next_cursor` 為 base64 編碼（內含排序鍵 + tie-break id），`has_more` 布林；`total_count` 恆為 `null`。

### 2.6 Null vs Absent Field 語意（強制）

每個 Response DTO 區分三態：`required`（永遠存在且非 null）、`null`（欄位存在值為 null）、`absent`（欄位不出現）。各端點 Response 欄位表均含 **Nullable** 欄。代表性規則：

| 欄位 | 型別 | 必填 | Nullable | 若為 null / absent 語意 |
|-----|------|------|---------|------------------------|
| `save` | Object | required | yes（null = 該 slot 無存檔） | `GET /save` 查無資料回 `{"save": null}` |
| `grade` | String | optional | yes（null = 未評級） | `leaderboard.grade` 未提供時為 null |
| `wallet`（錯誤回應中） | Object | absent if not 402 | no | 僅 402 餘額不足時附帶，其餘錯誤 absent |

### 2.7 Validation Error Priority Matrix（強制）

本系統沿用原型驗證語意：所有寫入端點採 **fail-fast**（第一個欄位驗證失敗即回傳 400，回應 `{"error":"<該欄位訊息>"}`），與 accounts.ts 逐項 `return` 一致。

| Endpoint | 驗證策略 | 第一順位檢查 |
|---------|---------|-------------|
| `POST /auth/register` | fail-fast | email 格式 → displayName 長度 → password 長度 |
| `POST /wallet/topup` | fail-fast | pack 有效性 / amount 範圍 |
| `POST /wallet/spend` | fail-fast | amount > 0 |
| `POST /entitlements/buy` | fail-fast | kind ∈ 集合 且 itemId 非空 |
| `POST /deposit/checkout` | fail-fast | packId ∈ 目錄 |
| `PUT /save` | fail-fast | data 非 null |
| `POST /records` | fail-fast | type ∈ 集合 |
| `POST /leaderboard` | fail-fast | runId 非空 |

### 2.7.1 Request Body 驗證規則（Bean Validation Schema，gencode 友善）

> 本節為 gencode / 自動化實作提供**機器可讀的輸入驗證 schema**。採 Jakarta Bean Validation（`jakarta.validation` 註解，Spring Boot `@Valid` 觸發）；數值 / 列舉 / 跨欄位條件以 constraint 欄說明。約束值對齊 CONSTANTS（SSOT），與各端點 §3 的 Request DTO 一致。違反任一約束 → fail-fast 回 400 `{"error":"<該欄位訊息>"}`（§2.7）。

**`POST /auth/register`** — `RegisterRequest`
| 欄位 | 型別 | 必填 | Bean Validation | 約束說明 |
|-----|------|:----:|-----------------|---------|
| `email` | String | ✔ | `@NotBlank @Email` | RFC 5322；伺服器 trim + lowercase；全域唯一（違反唯一 → 409，非 400） |
| `displayName` | String | ✔ | `@NotBlank @Size(min=1, max=24)` | `DISPLAY_NAME_MIN`/`MAX` |
| `password` | String | ✔ | `@NotBlank @Size(min=6)` | `PASSWORD_MIN`；scrypt 雜湊 |

**`POST /auth/login`** — `LoginRequest`
| 欄位 | 型別 | 必填 | Bean Validation | 約束說明 |
|-----|------|:----:|-----------------|---------|
| `email` | String | ✔ | `@NotBlank @Email` | trim + lowercase |
| `password` | String | ✔ | `@NotBlank` | 明文（TLS 傳輸），不洩露帳號是否存在 |

**`POST /wallet/topup`** — `TopupRequest`（`pack` 與 `currency`+`amount` 二擇一）
| 欄位 | 型別 | 必填 | Bean Validation | 約束說明 |
|-----|------|:----:|-----------------|---------|
| `pack` | String | 選填 | — | ∈ `{g_small,g_mid,g_big,cap_1000}`（deposit 目錄；程式碼層校驗） |
| `currency` | enum | 選填 | — | `cash`/`gems`；無 `pack` 時必填；非 `gems` 視為 `cash` |
| `amount` | Long | 選填 | `@Min(1) @Max(10_000_000)` | `TOPUP_AMOUNT_MIN`/`MAX`；無 `pack` 時必填 |

**`POST /wallet/spend`** — `SpendRequest`
| 欄位 | 型別 | 必填 | Bean Validation | 約束說明 |
|-----|------|:----:|-----------------|---------|
| `currency` | enum | ✔ | `@NotNull` | `cash`/`gems`（非 `gems` 視為 `cash`） |
| `amount` | long | ✔ | `@Positive` | > 0；取整 |
| `reason` | String | 選填 | `@Size(max=40)` | `SPEND_REASON_MAXLEN`；超過截斷；預設 `spend` |

**`POST /entitlements/buy`** — `BuyRequest`
| 欄位 | 型別 | 必填 | Bean Validation | 約束說明 |
|-----|------|:----:|-----------------|---------|
| `kind` | enum | ✔ | `@NotNull` | `cosmetic`/`dream`/`scenario` |
| `itemId` | String | ✔ | `@NotBlank` | 非空 |
| `currency` | enum | 選填 | — | `cash`/`gems`（預設 `cash`） |
| `amount` | Long | 選填 | `@PositiveOrZero` | ≥ 0（預設 0 = 免費授予，不寫 ledger） |

**`POST /deposit/checkout`** — `CheckoutRequest`（+ header `Idempotency-Key`）
| 欄位 | 型別 | 必填 | Bean Validation | 約束說明 |
|-----|------|:----:|-----------------|---------|
| `packId` | String | ✔ | `@NotBlank` | 須 ∈ `GET /deposit/packs` 目錄（程式碼層校驗） |
| `Idempotency-Key`（header） | String（UUID v4） | ✔ | `@NotBlank`（header 必填） | 缺失回 400；24h 保存；同 key 異 body 回 409 |

**`PUT /save`** — `SavePutRequest`
| 欄位 | 型別 | 必填 | Bean Validation | 約束說明 |
|-----|------|:----:|-----------------|---------|
| `slot` | String | 選填 | — | 存檔槽，預設 `main` |
| `data` | Map/Object | ✔ | `@NotNull` | null 回 400「缺 data」；body > 4MB（`REQUEST_BODY_MAX`）回 413 |

> 列舉（`Currency`/`EntitlementKind`/`RecordType`）由 Jackson 反序列化校驗，非法值回 400；`pack`/`packId` 的目錄成員資格與 `email` 唯一性屬業務規則，於 service 層校驗（非註解），分別回 400 / 409。

### 2.8 HA / Resilience 通用規範（所有端點適用，詳見 EDD §3.6）

**2.8.1 Timeout（強制，非無限）**

| Timeout 類型 | 值 | 說明 |
|-------------|-----|------|
| 連線建立 Timeout（client） | 2s | TCP/TLS 連線建立上限 |
| 讀取 Timeout（client read） | 5s | client 等待回應上限 |
| Server 端最大響應時間 | 3s | 超過回 503（防 thread pool 耗盡） |
| DB 查詢 Timeout | 2s | HikariCP + statement_timeout；超時回滾交易 |
| Redis 操作 Timeout | 200ms | cache miss 降級回源 DB（非 500） |

> 效能敏感端點（`POST /wallet/spend`、`POST /entitlements/buy` 含 `FOR UPDATE` 行鎖）鎖等待逾時 1s，超時回 503 並釋放鎖，避免 cascading failure。

**2.8.2 重試策略（client）**

| 項目 | 規則 |
|------|------|
| 可重試狀態碼 | 429、502、503、504（暫時性故障） |
| 不可重試狀態碼 | 400、401、402、404、409（語意性失敗，重試無效） |
| 最大重試次數 | 3 次 |
| 退避算法 | Exponential Backoff + Jitter：`delay = min(2^n × 200ms, 5s) × (0.5 + rand/2)` |
| 非冪等端點重試 | `POST /deposit/checkout` 重試須帶相同 `Idempotency-Key`（§7）；`POST /wallet/spend` 不建議自動重試（非冪等） |

**2.8.3 Circuit Breaker**

對外部依賴（PostgreSQL、Redis）呼叫採 Resilience4j Circuit Breaker：

| 參數 | 值 |
|------|-----|
| 錯誤率門檻 | 5 秒滑動窗內錯誤率 > 50% → Open |
| Open 後 fallback | 回 `503 {"error":"服務暫時不可用"}` + `Retry-After: 30` |
| 半開探測間隔 | 30s（半開放行少量請求探測） |
| Redis 降級 | Open 時 session/cache 查詢回源 DB（降級非中斷，EDD §3.6.4） |

**2.8.4 Graceful Shutdown（EDD §3.6.5）**

Pod 收到 `SIGTERM` 後：(1) Readiness Probe 立即轉 `DOWN`，Load Balancer 停止導流；(2) 不再接受新請求；(3) in-flight 請求最多等待 **30s** 完成後才退出（Spring Boot `server.shutdown=graceful`、`spring.lifecycle.timeout-per-shutdown-phase=30s`）；(4) Outbox 事件投遞於關閉前完成當前批次。確保 rolling update / scale / 節點驅逐期間 SLO 維持 99.9%（CONSTANTS §4）。

### 2.9 curl 快速範例（可執行）

> Base URL 以 Production 為例；token 為 `POST /auth/login` 回傳的 `token` 欄位（32B hex opaque session）。測試用帳號請向後端申請（Slack: #backend）。

```bash
# 註冊
curl -X POST https://api.yocat.sayyogames.com/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"boss@yocat.example","displayName":"喵喵科技","password":"s3cret!"}'

# 登入（取得 token）
curl -X POST https://api.yocat.sayyogames.com/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"boss@yocat.example","password":"s3cret!"}'

# 花費（帶 Bearer session token）
curl -X POST https://api.yocat.sayyogames.com/wallet/spend \
  -H "Authorization: Bearer 3f8c1d2ae9b74c0a1b2c3d4e5f60718293a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8" \
  -H "Content-Type: application/json" \
  -d '{"currency":"cash","amount":1250,"reason":"salary_day_42"}'

# 公開排行 Top 20
curl "https://api.yocat.sayyogames.com/leaderboard?mode=sandbox&limit=20"
```

---

## 3. Endpoints

> **組織原則（Spring Modulith HC-2）**：依所屬 Bounded Context 分組；每個 Endpoint 只屬於一個 BC；Path Prefix 對應其 Owning BC。所有需認證端點回應碼含 401；錢包類含 402；建立/衝突類含 409（CONSTANTS §10）。lang_stack=Java，DTO 以 Java `record` + `@Schema` 表達。

### BC: 平台底座（Health）Endpoints

> **Owning BC**：平台底座（非 BC，橫切） | **Path Prefix**：`/health/` | **Owned Tables**：無

#### `GET /health/live`

存活探針（Liveness）。回應程序是否存活，供 K8s livenessProbe 使用。

- **認證**：公開（不需 token）
- **權限**：無

**Response 200：**
```json
{ "status": "UP" }
```

**Possible Errors：**

| Error Code | HTTP Status | 觸發條件 | Response Body 範例 |
|-----------|-------------|---------|-------------------|
| `SERVICE_UNAVAILABLE` | 503 | 程序關閉中（Graceful Shutdown） | `{"status":"DOWN"}` |

---

#### `GET /health/ready`

就緒探針（Readiness）。檢查 DB / Redis 依賴是否可用，供 K8s readinessProbe 使用。

- **認證**：公開
- **權限**：無

**Response 200：**
```json
{ "status": "UP", "db": "UP", "redis": "UP" }
```

**Possible Errors：**

| Error Code | HTTP Status | 觸發條件 | Response Body 範例 |
|-----------|-------------|---------|-------------------|
| `NOT_READY` | 503 | DB 或 Redis 不可用（Pod 暫不接流量） | `{"status":"DOWN","db":"DOWN"}` |

---

### BC: member Endpoints

> **Owning BC**：member | **Path Prefix**：`/auth/` | **Owned Tables**：`member`: `accounts`, `sessions`

#### `POST /auth/register`

註冊新帳號。同交易建立 `accounts`，提交後經 `MemberRegistered` 事件由 wallet BC 建立錢包 + `init` 開帳 ledger（EDD §4.6.1）；簽發 session token（TTL 30 天）。

- **認證**：公開
- **權限**：無
- **冪等**：否（每次建立新帳號；email 唯一約束防重複）

**Request Body：**

| 欄位 | 型別 | 必填 | Nullable | 說明 |
|-----|------|------|---------|------|
| `email` | String | required | no | 登入識別，RFC 5322 e-mail；伺服器 trim + lowercase；全域唯一 |
| `displayName` | String | required | no | 公司/老闆名，1–24 字（`DISPLAY_NAME_MIN`/`MAX`） |
| `password` | String | required | no | 密碼，≥ 6 碼（`PASSWORD_MIN`）；scrypt 雜湊儲存 |

**Request DTO（Java）：**
```java
public record RegisterRequest(
    @NotBlank @Email String email,
    @NotBlank @Size(min = 1, max = 24) String displayName,
    @NotBlank @Size(min = 6) String password
) {}
```

**Request 範例：**
```json
{ "email": "boss@yocat.example", "displayName": "喵喵科技", "password": "s3cret!" }
```

**Response 200：**

| 欄位 | 型別 | 必填 | Nullable | 說明 |
|-----|------|------|---------|------|
| `token` | String | required | no | opaque session token（32B hex） |
| `account` | Object | required | no | `{id, email, display_name}` |
| `wallet` | Object | required | no | 初始錢包 `{cash:0, gems:0}` |

**Response DTO（Java）：**
```java
public record AccountView(String id, String email, String displayName) {}
public record WalletView(long cash, long gems) {}
public record AuthResponse(String token, AccountView account, WalletView wallet) {}
```

```json
{
  "token": "3f8c1d2ae9b74c0a1b2c3d4e5f60718293a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8",
  "account": { "id": "1024", "email": "boss@yocat.example", "display_name": "喵喵科技" },
  "wallet": { "cash": 0, "gems": 0 }
}
```

**業務邏輯步驟：**

1. 驗證 `email`（RFC regex）→ 不符回 400「email 格式不正確」
2. 驗證 `displayName` 長度 ∈ [1,24] → 不符回 400「公司/老闆名需 1–24 字」
3. 驗證 `password` 長度 ≥ 6 → 不符回 400「密碼至少 6 碼」
4. 查詢 `accounts` WHERE `email` = :email → 命中回 409「這個 email 已經註冊過了」
5. BEGIN TRANSACTION
6. INSERT INTO `accounts` (`email`, `display_name`, `pass_hash`) VALUES (…)（`pass_hash` = scrypt(salt$hash)，`SCRYPT_KEYLEN=64`、`SCRYPT_SALT_BYTES=16`）
7. INSERT event_publication `MemberRegistered` {accountId, email}（同交易，Outbox）
8. COMMIT
9. 簽發 session：INSERT INTO `sessions`(`token`, `account_id`, `expires_at`) VALUES (randomHex(32), :id, now()+30d)
10. 回傳 200 + `AuthResponse`（wallet 由 `MemberRegistered` 事件異步建立，回應以 `{cash:0,gems:0}` 呈現）

**Possible Errors：**

| Error Code | HTTP Status | 觸發條件 | Response Body 範例 |
|-----------|-------------|---------|-------------------|
| `VALIDATION_ERROR` | 400 | email 格式不符 / 名稱越界 / 密碼過短 | `{"error":"email 格式不正確"}` |
| `CONFLICT` | 409 | 該 email 已被註冊 | `{"error":"這個 email 已經註冊過了"}` |
| `RATE_LIMITED` | 429 | 同一 IP 每分鐘超過 5 次（CONSTANTS §6） | `{"error":"請求過於頻繁"}` |
| `INTERNAL_ERROR` | 500 | 未預期例外（交易回滾） | `{"error":"internal error"}` |

---

#### `POST /auth/login`

登入並簽發 session token。驗證 scrypt 密碼（`timingSafeEqual` 防時序攻擊）。

- **認證**：公開
- **權限**：無
- **冪等**：否（每次簽發新 session）

**Request Body：**

| 欄位 | 型別 | 必填 | Nullable | 說明 |
|-----|------|------|---------|------|
| `email` | String | required | no | 登入識別（trim + lowercase） |
| `password` | String | required | no | 明文密碼（TLS 傳輸） |

**Request DTO（Java）：**
```java
public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}
```

**Request 範例：**
```json
{ "email": "boss@yocat.example", "password": "s3cret!" }
```

**Response 200：** 同 `AuthResponse`（`token` + `account` + 當前 `wallet`）。
```json
{
  "token": "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2",
  "account": { "id": "1024", "email": "boss@yocat.example", "display_name": "喵喵科技" },
  "wallet": { "cash": 18750, "gems": 330 }
}
```

**Possible Errors：**

| Error Code | HTTP Status | 觸發條件 | Response Body 範例 |
|-----------|-------------|---------|-------------------|
| `UNAUTHORIZED` | 401 | 帳號不存在或密碼錯誤（不區分，防帳號枚舉） | `{"error":"email 或密碼錯誤"}` |
| `RATE_LIMITED` | 429 | 同一 IP 每分鐘超過 10 次（防暴力破解，CONSTANTS §6） | `{"error":"請求過於頻繁"}` |
| `INTERNAL_ERROR` | 500 | 未預期例外 | `{"error":"internal error"}` |

---

#### `POST /auth/logout`

登出，刪除當前 session token。**冪等**：重複呼叫或 token 已失效皆回 200。

- **認證**：需 Bearer token（但即使 token 無效亦回 200，冪等）
- **權限**：`member:self`

**Request Body：** 無（token 取自 `Authorization` header）

**Response 200：**
```json
{ "ok": true }
```

**Possible Errors：**

| Error Code | HTTP Status | 觸發條件 | Response Body 範例 |
|-----------|-------------|---------|-------------------|
| `INTERNAL_ERROR` | 500 | 未預期例外（刪 session 失敗） | `{"error":"internal error"}` |

> 注意：logout 刻意不回 401（冪等語意），即使 token 不存在也刪除成功（no-op）並回 200。

---

#### `GET /auth/me`

取得當前登入帳號資料，彙整 wallet 與 entitlements（跨 BC 經 Public API 彙整，非跨 BC JOIN）。

- **認證**：需 Bearer token
- **權限**：`member:self`
- **Cross-BC Dependencies**：讀 wallet BC（`WalletService.balanceOf(accountId)`）+ entitlement BC（`EntitlementService.listOf(accountId)`）；同步 Public API 呼叫，非跨 BC JOIN。降級策略：wallet/entitlement BC 不可用時，`wallet`/`entitlements` 欄位回最後已知值或空集合，`account` 仍回 200（member BC 自有資料）。

**Response 200：**

| 欄位 | 型別 | 必填 | Nullable | 說明 |
|-----|------|------|---------|------|
| `account` | Object | required | no | `{id, email, display_name}` |
| `wallet` | Object | required | no | `{cash, gems}` |
| `entitlements` | Array | required | no（可為空陣列） | 已購權益清單 |

**Response DTO（Java）：**
```java
public record EntitlementView(String kind, String itemId, String acquiredAt) {}
public record MeResponse(AccountView account, WalletView wallet, List<EntitlementView> entitlements) {}
```

```json
{
  "account": { "id": "1024", "email": "boss@yocat.example", "display_name": "喵喵科技" },
  "wallet": { "cash": 18750, "gems": 330 },
  "entitlements": [
    { "kind": "cosmetic", "itemId": "suit_gold", "acquiredAt": "2026-10-30T02:11:00Z" }
  ]
}
```

**Possible Errors：**

| Error Code | HTTP Status | 觸發條件 | Response Body 範例 |
|-----------|-------------|---------|-------------------|
| `UNAUTHORIZED` | 401 | 無 / 過期 / 無效 token | `{"error":"請先登入"}` |
| `INTERNAL_ERROR` | 500 | 未預期例外 | `{"error":"internal error"}` |

---

### BC: wallet Endpoints

> **Owning BC**：wallet | **Path Prefix**：`/wallet/` | **Owned Tables**：`wallet`: `wallets`, `wallet_ledger`

#### `POST /wallet/topup`

儲值（假付費）。支援預定義 `pack`（gem/cap 包，deposit BC 解析）或自訂 `currency` + `amount`。同交易加值並寫 `topup` ledger。

- **認證**：需 Bearer token
- **權限**：`wallet:write`（self）
- **冪等**：否（Phase A 直接加值；Phase B 真金流經 `POST /deposit/checkout` 以 orderId 冪等）

**Request Body：**（`pack` 與 `currency+amount` 二擇一）

| 欄位 | 型別 | 必填 | Nullable | 說明 |
|-----|------|------|---------|------|
| `pack` | String | optional | yes | 儲值包 ID：`g_small`/`g_mid`/`g_big`（gems）、`cap_1000`（cash），由 deposit BC 權威定義（CONSTANTS §7） |
| `currency` | String（enum） | optional | yes | `cash`/`gems`；無 `pack` 時必填；非 `gems` 一律視為 `cash` |
| `amount` | Integer | optional | yes | 自訂金額，範圍 `[1, 10_000_000]`（`TOPUP_AMOUNT_MIN`/`MAX`）；取整 |

**Request DTO（Java）：**
```java
public enum Currency { cash, gems }
public record TopupRequest(String pack, Currency currency, Long amount) {}
```

**Request 範例：**
```json
{ "pack": "g_mid" }
```

**Response 200：**
```json
{ "ok": true, "currency": "gems", "amount": 330, "balance": 660, "wallet": { "cash": 18750, "gems": 660 } }
```

**業務邏輯步驟：**

1. 驗證 JWT → 解析 `account_id`（opaque session → `sessions.account_id`）
2. 解析 `pack`：若 ∈ `GEM_PACKS` → currency=gems, amount=包面額；若 ∈ `CAP_PACKS` → currency=cash；否則採自訂 `currency`+`amount`
3. 驗證 `amount` ∈ `(0, 10_000_000]` → 否則回 400「金額不正確」
4. BEGIN TRANSACTION
5. UPDATE `wallets` SET `{cash|gems}` = `{cash|gems}` + :amount, `updated_at` = now() WHERE `account_id` = :id RETURNING balance
6. INSERT INTO `wallet_ledger` (`account_id`,`currency`,`delta`,`reason`,`balance`,`meta`) VALUES (:id, :currency, +:amount, `'topup'`, :newBal, `{label, fake:true}`)
7. PUBLISH Domain Event `WalletBalanceChanged` {accountId, currency, delta, balance, reason=topup}（Outbox）
8. COMMIT
9. 回傳 200 + `{ok, currency, amount, balance, wallet}`

**Possible Errors：**

| Error Code | HTTP Status | 觸發條件 | Response Body 範例 |
|-----------|-------------|---------|-------------------|
| `VALIDATION_ERROR` | 400 | amount ≤ 0 或 > 10,000,000 | `{"error":"金額不正確"}` |
| `UNAUTHORIZED` | 401 | 無 / 過期 token | `{"error":"請先登入"}` |
| `RATE_LIMITED` | 429 | 每帳號每分鐘超過 60 次（CONSTANTS §6） | `{"error":"請求過於頻繁"}` |
| `INTERNAL_ERROR` | 500 | 未預期例外（交易回滾） | `{"error":"internal error"}` |

---

#### `POST /wallet/spend`

花費（扣款）。以 `SELECT ... FOR UPDATE` 行鎖序列化，餘額不足回 402 並回傳現有 wallet（交易回滾，不產生半筆異動）。

- **認證**：需 Bearer token
- **權限**：`wallet:write`（self）
- **冪等**：否（每次扣款為業務預期；併發以行鎖序列化，EDD §4.5.6）

**Request Body：**

| 欄位 | 型別 | 必填 | Nullable | 說明 |
|-----|------|------|---------|------|
| `currency` | String（enum） | required | no | `cash`/`gems`（非 `gems` 視為 `cash`） |
| `amount` | Integer | required | no | 扣款金額，> 0（取整） |
| `reason` | String | optional | yes | 花費事由，超過 40 字截斷（`SPEND_REASON_MAXLEN`）；預設 `spend` |

**Request DTO（Java）：**
```java
public record SpendRequest(@NotNull Currency currency, @Positive long amount, String reason) {}
```

**Request 範例：**
```json
{ "currency": "cash", "amount": 1250, "reason": "salary_day_42" }
```

**Response 200：**
```json
{ "ok": true, "currency": "cash", "balance": 17500, "wallet": { "cash": 17500, "gems": 330 } }
```

**業務邏輯步驟：**

1. 驗證 session → 解析 `account_id`
2. 驗證 `amount` > 0 → 否則回 400「金額不正確」
3. 截斷 `reason` 至 40 字（`SPEND_REASON_MAXLEN`）
4. BEGIN TRANSACTION
5. SELECT `{cash|gems}` FROM `wallets` WHERE `account_id` = :id **FOR UPDATE**（行鎖）
6. 若當前餘額 < :amount → ROLLBACK → 回 402「餘額不足」+ 現有 `wallet`
7. UPDATE `wallets` SET `{cash|gems}` = `{cash|gems}` - :amount, `updated_at` = now() RETURNING balance
8. INSERT INTO `wallet_ledger` (`account_id`,`currency`,`delta`,`reason`,`balance`,`meta`) VALUES (:id, :currency, -:amount, :reason, :newBal, :meta)
9. PUBLISH `WalletBalanceChanged` {accountId, currency, delta=-amount, balance, reason}（Outbox）
10. COMMIT → 回傳 200 + `{ok, currency, balance, wallet}`

**Possible Errors：**

| Error Code | HTTP Status | 觸發條件 | Response Body 範例 |
|-----------|-------------|---------|-------------------|
| `VALIDATION_ERROR` | 400 | amount ≤ 0 | `{"error":"金額不正確"}` |
| `UNAUTHORIZED` | 401 | 無 / 過期 token | `{"error":"請先登入"}` |
| `PAYMENT_REQUIRED` | 402 | 餘額 < amount（交易回滾） | `{"error":"餘額不足","wallet":{"cash":500,"gems":0}}` |
| `RATE_LIMITED` | 429 | 每帳號每分鐘超過 60 次 | `{"error":"請求過於頻繁"}` |
| `INTERNAL_ERROR` | 500 | 未預期例外（交易回滾） | `{"error":"internal error"}` |

---

### BC: deposit Endpoints

> **Owning BC**：deposit | **Path Prefix**：`/deposit/`（另 `POST /wallet/topup` 的 pack 解析亦屬 deposit 權威） | **Owned Tables**：`deposit`: `deposit_pack`（目錄）、`deposit_order`（Phase B）

#### `GET /deposit/packs`

列出可購買的儲值包目錄（伺服器權威定義，含 gem 包與 cap 包）。供前端商店畫面渲染。

- **認證**：公開（商店目錄）
- **權限**：無

**Response 200：**

| 欄位 | 型別 | 必填 | Nullable | 說明 |
|-----|------|------|---------|------|
| `packs` | Array | required | no | 儲值包清單 |
| `packs[].packId` | String | required | no | 包 ID（`g_small`/`g_mid`/`g_big`/`cap_1000`） |
| `packs[].currency` | String（enum） | required | no | `gems`/`cash` |
| `packs[].amount` | Integer | required | no | 入帳數量（CONSTANTS §7） |
| `packs[].label` | String | required | no | 顯示名稱（zh-TW） |
| `packs[].bonusPct` | Integer | optional | yes（null = 無加贈） | 加量比例（g_mid=10、g_big=16，CONSTANTS §2） |

**Response DTO（Java）：**
```java
public record DepositPackView(String packId, Currency currency, long amount, String label, Integer bonusPct) {}
public record DepositPackListResponse(List<DepositPackView> packs) {}
```

```json
{
  "packs": [
    { "packId": "g_small", "currency": "gems", "amount": 60,  "label": "小寶石包",        "bonusPct": null },
    { "packId": "g_mid",   "currency": "gems", "amount": 330, "label": "中寶石包（+10%）", "bonusPct": 10 },
    { "packId": "g_big",   "currency": "gems", "amount": 700, "label": "大寶石包（+16%）", "bonusPct": 16 },
    { "packId": "cap_1000","currency": "cash", "amount": 1000,"label": "儲值 1000 資本（測試）", "bonusPct": null }
  ]
}
```

**Possible Errors：**

| Error Code | HTTP Status | 觸發條件 | Response Body 範例 |
|-----------|-------------|---------|-------------------|
| `INTERNAL_ERROR` | 500 | 目錄載入失敗 | `{"error":"internal error"}` |

---

#### `POST /deposit/checkout`

建立儲值訂單（Phase A 假付費直接確認 → 由 `DepositConfirmed` 事件 credit 錢包；Phase B 對接真金流 SDK）。**冪等**：以 `Idempotency-Key`（= orderId）去重，重放回放原結果（EDD §4.6.1 orderId dedup）。

- **認證**：需 Bearer token
- **權限**：`deposit:write`（self）
- **冪等**：是（`Idempotency-Key` 必填，24h 保存；同 key 重放只入帳一次）
- **Cross-BC Dependencies**：異步發布 `DepositConfirmed` 事件 → wallet BC 消費並 credit（Outbox，EDD §4.6.1）。降級策略：wallet BC 暫不可用時事件留存 Outbox（`event_publication`），wallet 恢復後補投（最多 5 次重試，event_id 冪等），不丟帳。

**Request Headers（額外必填）：**
```http
Idempotency-Key: 7d793037-a076-4c05-9a70-b7e8b2e3c4f5
```

**Request Body：**

| 欄位 | 型別 | 必填 | Nullable | 說明 |
|-----|------|------|---------|------|
| `packId` | String | required | no | 儲值包 ID，須 ∈ `GET /deposit/packs` 目錄 |

**Request DTO（Java）：**
```java
public record CheckoutRequest(@NotBlank String packId) {}
```

**Request 範例：**
```json
{ "packId": "g_big" }
```

**Response 200：**

| 欄位 | 型別 | 必填 | Nullable | 說明 |
|-----|------|------|---------|------|
| `orderId` | String | required | no | 訂單 ID（= Idempotency-Key 或伺服器產生 UUID） |
| `packId` | String | required | no | 儲值包 ID |
| `currency` | String（enum） | required | no | `gems`/`cash` |
| `amount` | Integer | required | no | 入帳數量 |
| `status` | String（enum） | required | no | `confirmed`（Phase A 假付費恆 confirmed；Phase B 可為 `pending`） |

**Response DTO（Java）：**
```java
public enum OrderStatus { pending, confirmed, failed }
public record CheckoutResponse(String orderId, String packId, Currency currency, long amount, OrderStatus status) {}
```

```json
{ "orderId": "7d793037-a076-4c05-9a70-b7e8b2e3c4f5", "packId": "g_big", "currency": "gems", "amount": 700, "status": "confirmed" }
```

**業務邏輯步驟：**

1. 驗證 session → 解析 `account_id`
2. 讀取 `Idempotency-Key`（缺失回 400）；查詢 `deposit_order` WHERE `idempotency_key` = :key → 命中則回放原結果（200）
3. 驗證 `packId` ∈ deposit 目錄 → 否則回 400「儲值包不正確」
4. BEGIN TRANSACTION
5. INSERT INTO `deposit_order` (`account_id`,`pack_id`,`currency`,`amount`,`status`,`idempotency_key`) VALUES (…, `'confirmed'`, :key)
6. PUBLISH `DepositConfirmed` {accountId, orderId, currency, amount}（Outbox，orderId dedup）
7. COMMIT
8. wallet BC 異步消費 `DepositConfirmed` → credit `wallets` + INSERT `wallet_ledger`(reason=`topup`)（event_id 冪等，最多 5 次重試指數退避）
9. 回傳 200 + `CheckoutResponse`

**Possible Errors：**

| Error Code | HTTP Status | 觸發條件 | Response Body 範例 |
|-----------|-------------|---------|-------------------|
| `VALIDATION_ERROR` | 400 | packId 不在目錄 / 缺 Idempotency-Key | `{"error":"儲值包不正確"}` |
| `UNAUTHORIZED` | 401 | 無 / 過期 token | `{"error":"請先登入"}` |
| `IDEMPOTENCY_CONFLICT` | 409 | 同 Idempotency-Key 但 body 不同 | `{"error":"idempotency key 衝突"}` |
| `RATE_LIMITED` | 429 | 每帳號每分鐘超過 60 次 | `{"error":"請求過於頻繁"}` |
| `INTERNAL_ERROR` | 500 | 未預期例外（交易回滾） | `{"error":"internal error"}` |

---

### BC: entitlement Endpoints

> **Owning BC**：entitlement | **Path Prefix**：`/entitlements` | **Owned Tables**：`entitlement`: `entitlements`

#### `GET /entitlements`

列出當前帳號已購權益（破產不消失，PRD §5.3）。

- **認證**：需 Bearer token
- **權限**：`entitlement:read`（self）

**Response 200：**

| 欄位 | 型別 | 必填 | Nullable | 說明 |
|-----|------|------|---------|------|
| `entitlements` | Array | required | no（可空陣列） | 權益清單，依 `acquired_at` 升序 |
| `entitlements[].kind` | String（enum） | required | no | `cosmetic`/`dream`/`scenario`（CONSTANTS §9） |
| `entitlements[].itemId` | String | required | no | 項目 ID |
| `entitlements[].acquiredAt` | String（ISO 8601） | required | no | 取得時間 |

**Response DTO（Java）：** `record EntitlementListResponse(List<EntitlementView> entitlements) {}`

```json
{
  "entitlements": [
    { "kind": "cosmetic", "itemId": "suit_gold", "acquiredAt": "2026-10-30T02:11:00Z" },
    { "kind": "dream", "itemId": "soul_einstein", "acquiredAt": "2026-10-30T05:40:00Z" }
  ]
}
```

**Possible Errors：**

| Error Code | HTTP Status | 觸發條件 | Response Body 範例 |
|-----------|-------------|---------|-------------------|
| `UNAUTHORIZED` | 401 | 無 / 過期 token | `{"error":"請先登入"}` |
| `INTERNAL_ERROR` | 500 | 未預期例外 | `{"error":"internal error"}` |

---

#### `POST /entitlements/buy`

購買權益（原子：扣款 + 授予）。同交易呼叫 `WalletService.debit()`（跨 BC Public API，EDD §3.4）；`amount=0` 為活動免費授予（僅插權益、不寫 ledger，CONSTANTS §7）。

- **認證**：需 Bearer token
- **權限**：`entitlement:write`（self）
- **冪等**：是（`UNIQUE(account_id, kind, item_id)`；已擁有回 409）
- **Cross-BC Dependencies**：同步呼叫 wallet BC `WalletService.debit(accountId, currency, amount)`（同交易原子扣款，EDD §3.4 Customer/Supplier）。降級策略：wallet BC 不可用時整筆交易回滾（不授予權益），回 503，client 可重試（授予未發生，安全）。

**Request Body：**

| 欄位 | 型別 | 必填 | Nullable | 說明 |
|-----|------|------|---------|------|
| `kind` | String（enum） | required | no | `cosmetic`/`dream`/`scenario`；不在集合回 400 |
| `itemId` | String | required | no | 項目 ID，非空 |
| `currency` | String（enum） | optional | yes | `cash`/`gems`（預設 `cash`） |
| `amount` | Integer | optional | yes | 價格，≥ 0（預設 0 = 免費授予） |

**Request DTO（Java）：**
```java
public enum EntitlementKind { cosmetic, dream, scenario }
public record BuyRequest(@NotNull EntitlementKind kind, @NotBlank String itemId, Currency currency, Long amount) {}
```

**Request 範例：**
```json
{ "kind": "cosmetic", "itemId": "suit_gold", "currency": "gems", "amount": 120 }
```

**Response 200：**
```json
{
  "ok": true,
  "wallet": { "cash": 18750, "gems": 210 },
  "entitlements": [
    { "kind": "cosmetic", "itemId": "suit_gold", "acquiredAt": "2026-10-30T02:11:00Z" }
  ]
}
```

**業務邏輯步驟：**

1. 驗證 session → 解析 `account_id`
2. 驗證 `kind` ∈ {cosmetic,dream,scenario} 且 `itemId` 非空 → 否則回 400「項目不正確」
3. BEGIN TRANSACTION
4. SELECT 1 FROM `entitlements` WHERE `account_id`,`kind`,`item_id` → 命中則 ROLLBACK → 回 409「已經擁有這個項目」
5. 若 `amount` > 0：SELECT `{cash|gems}` FROM `wallets` WHERE `account_id` **FOR UPDATE** → 餘額 < amount → ROLLBACK → 回 402「餘額不足」+ wallet
6. UPDATE `wallets` SET `{cash|gems}` = `{cash|gems}` - :amount；INSERT `wallet_ledger`(delta=-amount, reason=`'buy_'+kind`, balance, meta={itemId})
7. INSERT INTO `entitlements` (`account_id`,`kind`,`item_id`)
8. PUBLISH `EntitlementGranted` {accountId, kind, itemId}（Outbox）
9. COMMIT → 回傳 200 + `{ok, wallet, entitlements}`

**Possible Errors：**

| Error Code | HTTP Status | 觸發條件 | Response Body 範例 |
|-----------|-------------|---------|-------------------|
| `VALIDATION_ERROR` | 400 | kind 不在集合 / itemId 空 | `{"error":"項目不正確"}` |
| `UNAUTHORIZED` | 401 | 無 / 過期 token | `{"error":"請先登入"}` |
| `PAYMENT_REQUIRED` | 402 | 餘額 < amount（交易回滾） | `{"error":"餘額不足","wallet":{"cash":50,"gems":0}}` |
| `CONFLICT` | 409 | 已擁有 `(kind,item_id)` | `{"error":"已經擁有這個項目"}` |
| `RATE_LIMITED` | 429 | 每帳號每分鐘超過 30 次（CONSTANTS §6） | `{"error":"請求過於頻繁"}` |
| `INTERNAL_ERROR` | 500 | 未預期例外（交易回滾） | `{"error":"internal error"}` |

---

### BC: record Endpoints

> **Owning BC**：record | **Path Prefix**：`/records` | **Owned Tables**：`record`: `records`

#### `POST /records`

記錄經營史（破產 / 成就 / 傳承），append-only（PRD §4.1）。

- **認證**：需 Bearer token
- **權限**：`record:write`（self）
- **冪等**：否（append-only，每次新增一筆）

**Request Body：**

| 欄位 | 型別 | 必填 | Nullable | 說明 |
|-----|------|------|---------|------|
| `type` | String（enum） | required | no | `bankruptcy`/`achievement`/`legacy`（CONSTANTS §9）；不在集合回 400 |
| `payload` | Object（JSON） | optional | yes | 記錄內容（任意結構，預設 `{}`） |

**Request DTO（Java）：**
```java
public enum RecordType { bankruptcy, achievement, legacy }
public record RecordRequest(@NotNull RecordType type, Map<String, Object> payload) {}
```

**Request 範例：**
```json
{ "type": "bankruptcy", "payload": { "day": 42, "finalCash": -15200, "reason": "資金鏈斷裂" } }
```

**Response 200：**
```json
{ "ok": true }
```

**Possible Errors：**

| Error Code | HTTP Status | 觸發條件 | Response Body 範例 |
|-----------|-------------|---------|-------------------|
| `VALIDATION_ERROR` | 400 | type 不在集合 | `{"error":"type 不正確"}` |
| `UNAUTHORIZED` | 401 | 無 / 過期 token | `{"error":"請先登入"}` |
| `INTERNAL_ERROR` | 500 | 未預期例外 | `{"error":"internal error"}` |

---

#### `GET /records`

查詢經營史（可依 `type` 篩選），依 `created_at DESC` 排序，單次最多 100 筆（CONSTANTS §7）。

- **認證**：需 Bearer token
- **權限**：`record:read`（self）

**Query Parameters：**

| 參數 | 型別 | 必填 | 說明 | 預設 |
|------|------|------|------|------|
| `type` | String（enum） | 否 | `bankruptcy`/`achievement`/`legacy`；省略則回全部類型 | — |

**Response 200：**

| 欄位 | 型別 | 必填 | Nullable | 說明 |
|-----|------|------|---------|------|
| `records` | Array | required | no（可空陣列） | 記錄清單（≤ 100） |
| `records[].type` | String（enum） | required | no | 記錄類型 |
| `records[].payload` | Object | required | no | 記錄內容 |
| `records[].createdAt` | String（ISO 8601） | required | no | 建立時間 |

**Response DTO（Java）：**
```java
public record BusinessRecordView(RecordType type, Map<String, Object> payload, String createdAt) {}
public record RecordListResponse(List<BusinessRecordView> records) {}
```

```json
{
  "records": [
    { "type": "bankruptcy", "payload": { "day": 42, "finalCash": -15200 }, "createdAt": "2026-10-30T06:00:00Z" }
  ]
}
```

**Possible Errors：**

| Error Code | HTTP Status | 觸發條件 | Response Body 範例 |
|-----------|-------------|---------|-------------------|
| `UNAUTHORIZED` | 401 | 無 / 過期 token | `{"error":"請先登入"}` |
| `INTERNAL_ERROR` | 500 | 未預期例外 | `{"error":"internal error"}` |

---

### BC: leaderboard Endpoints

> **Owning BC**：leaderboard | **Path Prefix**：`/leaderboard` | **Owned Tables**：`leaderboard`: `leaderboard`

#### `GET /leaderboard`

公開查詢全域排行 Top N（不需登入），依 `score DESC`（索引 `idx_lb_mode_score`）。

- **認證**：公開
- **權限**：無

**Query Parameters：**

| 參數 | 型別 | 必填 | 說明 | 預設 |
|------|------|------|------|------|
| `mode` | String | 否 | 排行模式；空字串 = 全模式（CONSTANTS §9） | `''`（全模式） |
| `limit` | Integer | 否 | 回傳筆數，夾擠 `[1,100]`（`LEADERBOARD_LIMIT_MAX`） | 20（`LEADERBOARD_LIMIT_DEFAULT`） |

**Response 200：**

| 欄位 | 型別 | 必填 | Nullable | 說明 |
|-----|------|------|---------|------|
| `leaderboard` | Array | required | no（可空陣列） | Top N 清單 |
| `leaderboard[].runId` | String | required | no | 場次 ID |
| `leaderboard[].name` | String | required | no | 上榜顯示名（display_name 歸戶） |
| `leaderboard[].mode` | String | required | no | 模式 |
| `leaderboard[].score` | Integer | required | no | 分數 |
| `leaderboard[].grade` | String | optional | yes（null = 未評級） | 評級 |
| `leaderboard[].updatedAt` | String（ISO 8601） | required | no | 更新時間 |

**Response DTO（Java）：**
```java
public record LeaderboardEntryView(String runId, String name, String mode, long score, String grade, String updatedAt) {}
public record LeaderboardResponse(List<LeaderboardEntryView> leaderboard) {}
```

```json
{
  "leaderboard": [
    { "runId": "run-7781", "name": "喵喵科技", "mode": "sandbox", "score": 982300, "grade": "S", "updatedAt": "2026-10-30T07:20:00Z" },
    { "runId": "run-5520", "name": "汪汪企業", "mode": "sandbox", "score": 870100, "grade": null, "updatedAt": "2026-10-30T07:10:00Z" }
  ]
}
```

**Possible Errors：**

| Error Code | HTTP Status | 觸發條件 | Response Body 範例 |
|-----------|-------------|---------|-------------------|
| `RATE_LIMITED` | 429 | 同一 IP 每分鐘超過 120 次（防爬取，CONSTANTS §6） | `{"error":"請求過於頻繁"}` |
| `INTERNAL_ERROR` | 500 | 未預期例外 | `{"error":"internal error"}` |

---

#### `POST /leaderboard`

上榜（歸戶寫入），以 `run_id` upsert。**冪等**：同 `run_id` 重放只更新一筆（EDD §5.1）。

- **認證**：需 Bearer token
- **權限**：`leaderboard:write`（self）
- **冪等**：是（by `run_id`，`ON CONFLICT (run_id) DO UPDATE`）

**Request Body：**

| 欄位 | 型別 | 必填 | Nullable | 說明 |
|-----|------|------|---------|------|
| `runId` | String | required | no | 場次 ID（upsert 鍵）；空字串回 400 |
| `mode` | String | optional | yes | 排行模式，預設 `sandbox` |
| `score` | Integer | optional | yes | 分數，取整，預設 0 |
| `grade` | String | optional | yes | 評級 |
| `detail` | Object（JSON） | optional | yes | 明細，預設 `{}` |

**Request DTO（Java）：**
```java
public record LeaderboardSubmitRequest(@NotBlank String runId, String mode, Long score, String grade, Map<String, Object> detail) {}
```

**Request 範例：**
```json
{ "runId": "run-7781", "mode": "sandbox", "score": 982300, "grade": "S", "detail": { "days": 120, "peakWorkers": 8 } }
```

**Response 200：**
```json
{ "ok": true }
```

**業務邏輯步驟：**

1. 驗證 session → 解析 `account_id` + `display_name`（歸戶）
2. 驗證 `runId` 非空 → 否則回 400「缺 runId」
3. `mode` 預設 `sandbox`；`score` 取整
4. INSERT INTO `leaderboard`(`run_id`,`account_id`,`display_name`,`mode`,`score`,`grade`,`detail`,`updated_at`) VALUES (…) **ON CONFLICT (`run_id`) DO UPDATE** SET `score`,`grade`,`detail`,`mode`,`updated_at`=now()（冪等 upsert）
5. PUBLISH `RunScoreSubmitted` {accountId, runId, mode, score}（Outbox，EDD §3.4）
6. 回傳 200 + `{ok:true}`

**Possible Errors：**

| Error Code | HTTP Status | 觸發條件 | Response Body 範例 |
|-----------|-------------|---------|-------------------|
| `VALIDATION_ERROR` | 400 | runId 空 | `{"error":"缺 runId"}` |
| `UNAUTHORIZED` | 401 | 無 / 過期 token | `{"error":"請先登入"}` |
| `INTERNAL_ERROR` | 500 | 未預期例外 | `{"error":"internal error"}` |

---

### BC: game-save Endpoints

> **Owning BC**：game-save | **Path Prefix**：`/save` | **Owned Tables**：`game_save`: `saves`

#### `GET /save`

取得辦公室存檔（跨裝置還原），指定 `slot`（預設 `main`）。

- **認證**：需 Bearer token
- **權限**：`game-save:read`（self）

**Query Parameters：**

| 參數 | 型別 | 必填 | 說明 | 預設 |
|------|------|------|------|------|
| `slot` | String | 否 | 存檔槽（CONSTANTS §7） | `main` |

**Response 200：**

| 欄位 | 型別 | 必填 | Nullable | 說明 |
|-----|------|------|---------|------|
| `save` | Object | required | yes（**null = 該 slot 無存檔**） | 存檔內容 |
| `save.data` | Object（JSON） | required（若 save≠null） | no | 存檔資料 |
| `save.updatedAt` | String（ISO 8601） | required（若 save≠null） | no | 最後更新時間 |

**Response DTO（Java）：**
```java
public record SaveView(Map<String, Object> data, String updatedAt) {}
public record SaveResponse(SaveView save) {} // save 可為 null
```

```json
{ "save": { "data": { "day": 42, "cash": 18750, "morale": 70 }, "updatedAt": "2026-10-30T08:00:00Z" } }
```

查無存檔：
```json
{ "save": null }
```

**Possible Errors：**

| Error Code | HTTP Status | 觸發條件 | Response Body 範例 |
|-----------|-------------|---------|-------------------|
| `UNAUTHORIZED` | 401 | 無 / 過期 token | `{"error":"請先登入"}` |
| `INTERNAL_ERROR` | 500 | 未預期例外 | `{"error":"internal error"}` |

---

#### `PUT /save`

上傳/覆寫辦公室存檔（upsert by `(account_id, slot)`）。**冪等**：相同請求重複執行結果相同。請求 body > 4MB（`REQUEST_BODY_MAX`）中斷連線。

- **認證**：需 Bearer token
- **權限**：`game-save:write`（self）
- **冪等**：是（`ON CONFLICT (account_id, slot) DO UPDATE`）

**Request Body：**

| 欄位 | 型別 | 必填 | Nullable | 說明 |
|-----|------|------|---------|------|
| `slot` | String | optional | yes | 存檔槽，預設 `main` |
| `data` | Object（JSON） | required | no | 存檔資料；null 回 400「缺 data」 |

**Request DTO（Java）：**
```java
public record SavePutRequest(String slot, @NotNull Map<String, Object> data) {}
```

**Request 範例：**
```json
{ "slot": "main", "data": { "day": 42, "cash": 18750, "morale": 70, "roster": ["m1","m2","m3"] } }
```

**Response 200：**
```json
{ "ok": true }
```

**Possible Errors：**

| Error Code | HTTP Status | 觸發條件 | Response Body 範例 |
|-----------|-------------|---------|-------------------|
| `VALIDATION_ERROR` | 400 | data 為 null / 缺失 | `{"error":"缺 data"}` |
| `UNAUTHORIZED` | 401 | 無 / 過期 token | `{"error":"請先登入"}` |
| `PAYLOAD_TOO_LARGE` | 413 | body > 4,000,000 bytes（`REQUEST_BODY_MAX`，連線中斷） | （連線中斷，無 body） |
| `INTERNAL_ERROR` | 500 | 未預期例外 | `{"error":"internal error"}` |

---

## 4. Authentication & Authorization

### 4.1 認證機制（Opaque Session Token — EDD ADR-003）

本系統採 **opaque session token**（非 JWT）。登入/註冊簽發 32 bytes hex token，存 `sessions` 表 + Redis 快取（miss 回源 DB），TTL 30 天（`SESSION_TTL_DAYS`）。選此方案以支援即時撤銷（客服強制登出 / logout）。

**取得 token：**
- `POST /auth/register`（新帳號）或 `POST /auth/login`（既有帳號）→ Response 含 `token`。

**使用 token：**
```http
Authorization: Bearer 3f8c1d2ae9b74c0a1b2c3d4e5f60718293a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8
```

**撤銷 token：**
- `POST /auth/logout`（刪除 session，冪等）。
- token 過期（> 30 天）自動失效（`expires_at` < now()）。

> **無 Refresh Token**：本系統不使用 JWT access/refresh 雙 token 模式；session TTL 30 天，過期後需重新 `POST /auth/login`。此與原型及 EDD ADR-003 一致。

### 4.2 Token 生命週期

| Token 類型 | 有效期 | 儲存位置 | 撤銷方式 |
|-----------|--------|---------|---------|
| `session_token`（opaque） | 30 天（`SESSION_TTL_DAYS`，可環境變數覆寫） | `sessions` 表 + Redis 快取 | `POST /auth/logout` 或過期 |

### 4.3 密碼安全

- 雜湊：scrypt（`SCRYPT_KEYLEN=64` bytes、`SCRYPT_SALT_BYTES=16` bytes），儲存格式 `salt$hash`（hex）。
- 驗證：`crypto.timingSafeEqual`（Java 以 `MessageDigest.isEqual` 等時比較）防時序攻擊。
- 登入失敗不區分「帳號不存在」與「密碼錯誤」（統一 401「email 或密碼錯誤」），防帳號枚舉。

### 4.4 授權模型（RBAC）

本系統為 C 端信任層，權限以「資源擁有者（self）」為主；EDD §3.3 定義 `USER`（終端）與 `OPS`（營運/客服）兩角色。

| Role | 說明 | 可操作範圍 |
|------|------|-----------|
| `USER` | 遊戲客戶端代表的終端玩家 | 僅能存取自己帳號的 wallet / entitlement / record / leaderboard / save |
| `OPS` | 營運 / 客服 | 對帳稽核唯讀 + 強制登出（Phase B Admin，本文件 §18 不含 Admin 端點） |

> 所有需認證端點強制「資源歸屬檢查」：`account_id` 一律取自 session，**不信任 client 傳入的 account_id**（防越權存取他人錢包/存檔）。

### 4.5 公開端點清單

`POST /auth/register`、`POST /auth/login`、`GET /leaderboard`、`GET /deposit/packs`、`GET /health/live`、`GET /health/ready`。其餘端點無 / 過期 token 一律回 401（logout 除外，冪等回 200）。

---

## 5. Error Handling

### 5.1 Error Response Schema

本系統 api-only 契約沿用原型統一錯誤格式（EDD §5.1）：

```json
{ "error": "<zh-TW 人類可讀訊息>" }
```

- 錢包相關 402 另附當前 `wallet`：`{ "error": "餘額不足", "wallet": { "cash": 0, "gems": 0 } }`。
- 500 不洩露內部堆疊 / SQL（僅回安全訊息字串 + 以 `X-Request-Id` 供追蹤）。
- 訊息可 i18n key 化（`Accept-Language` 預設 zh-TW）。

### 5.2 Error Code Registry（全域，CONSTANTS §10 / EDD §5.4）

| HTTP Status | 語意代號（client 處理用） | 觸發情境 | 回應範例 |
|-------------|--------------------------|---------|---------|
| 400 | `VALIDATION_ERROR` | email 格式、名稱越界、密碼過短、金額不正確、kind/type 非法、缺 data/runId、packId 不正確 | `{"error":"金額不正確"}` |
| 401 | `UNAUTHORIZED` | 無 / 過期 / 無效 token；登入帳密錯 | `{"error":"請先登入"}` / `{"error":"email 或密碼錯誤"}` |
| 402 | `PAYMENT_REQUIRED` | spend / buy 餘額 < amount | `{"error":"餘額不足","wallet":{...}}` |
| 404 | `NOT_FOUND` | 命中前綴但無對應路由 | `{"error":"api not found"}` |
| 409 | `CONFLICT` | email 已註冊；已擁有 `(kind,item_id)`；Idempotency-Key 衝突 | `{"error":"這個 email 已經註冊過了"}` |
| 413 | `PAYLOAD_TOO_LARGE` | 請求 body > 4MB（`REQUEST_BODY_MAX`） | （連線中斷） |
| 429 | `RATE_LIMITED` | 超過速率限制（§6） | `{"error":"請求過於頻繁"}` + `Retry-After` |
| 500 | `INTERNAL_ERROR` | 未預期例外（交易回滾） | `{"error":"internal error"}` |
| 503 | `SERVICE_UNAVAILABLE` | 依賴不可用 / Graceful Shutdown | `{"error":"服務暫時不可用"}` + `Retry-After` |

> 各 Endpoint 的 Possible Errors 表（§3）均引用本 Registry 語意；每個需認證端點含 401、錢包類含 402、建立/衝突類含 409。

> **關於 403 Forbidden（刻意不使用）：** 本系統 C 端信任層所有資源均為「帳號自有（self-scoped）」，`account_id` 一律取自 session，client 無法指定他人資源，故不存在「已認證但無權限存取他人資源」的 403 情境。SSOT（CONSTANTS §10）亦未登記 403。未認證一律回 401；越權嘗試（偽造他人 token）等同無效 token → 401。OPS 角色的後台稽核屬 Phase B Admin（本契約不含），屆時於 Admin API 引入 403。

---

## 6. Rate Limiting

> 現行原型尚未實作端點層 rate limit（CONSTANTS §6）；下列為 EDD §9 安全層須落地值（CONSTANTS §6 建議值，Spring Bucket4j + Redis 實作）。

### 6.1 限制層級（三層）

| 層級 | 限制對象 | 限制規則 |
|------|---------|---------|
| Per-IP（認證端點） | 客戶端 IP | `POST /auth/login` 10 req/min；`POST /auth/register` 5 req/min |
| Per-IP（公開查詢） | 客戶端 IP | `GET /leaderboard` 120 req/min |
| Per-User（金流/業務） | 帳號 account_id | `/wallet/topup`、`/wallet/spend`、`/deposit/checkout` 60 req/min；`/entitlements/buy` 30 req/min |
| Global（全局保護） | 全服務 | 依 HPA 容量（500 → 3000 PCU，CONSTANTS §8）動態上限，過載回 503 |

### 6.2 Burst 容許機制

採 Token Bucket；短暫突發容許至桶容量上限（例：login 平均 10/min，burst 桶容量 3/10s），超過即 429。

### 6.3 429 Response 格式

```http
HTTP/1.1 429 Too Many Requests
X-RateLimit-Limit: 60
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1793664060
Retry-After: 42
Content-Type: application/json; charset=utf-8

{ "error": "請求過於頻繁" }
```

---

## 7. Idempotency

### 7.1 設計原則

冪等確保重試不產生重複副作用。本系統冪等來源有二：**天然冪等**（upsert / UNIQUE 約束）與 **Idempotency-Key**（金流）。

### 7.2 冪等端點清單（EDD §5.1 / §4.5.6）

| 端點 | 冪等依據 | 行為 |
|------|---------|------|
| `POST /auth/logout` | by token | 重複/無效 token 皆回 200（no-op） |
| `PUT /save` | by `(account_id, slot)` | `ON CONFLICT DO UPDATE` |
| `POST /leaderboard` | by `run_id` | `ON CONFLICT (run_id) DO UPDATE` |
| `POST /entitlements/buy` | by `UNIQUE(account_id,kind,item_id)` | 已擁有回 409 |
| `POST /deposit/checkout` | by `Idempotency-Key`（= orderId） | 重放回放原結果；Domain Event 以 `event_id`/`orderId` dedup |

> **非冪等**：`POST /wallet/spend`（每次扣款為業務預期；併發以 `SELECT FOR UPDATE` 行鎖序列化）、`POST /wallet/topup`（Phase A）、`POST /records`（append-only）。

### 7.3 伺服器行為（Idempotency-Key）

1. 以 `Idempotency-Key` 為索引查詢 `deposit_order`。
2. 命中且 body 相同 → 回放原始 Response（不重複執行）。
3. 未命中 → 執行邏輯，保存結果（TTL 24 小時）。
4. 命中但 body 不同 → 回 409「idempotency key 衝突」。

### 7.4 Key 規範

```http
Idempotency-Key: 7d793037-a076-4c05-9a70-b7e8b2e3c4f5
```

- 格式：UUID v4（client 產生）；生命週期 24 小時；每 key 對應單一帳號 + 端點。

---

## 8. Webhooks

> **Phase A：本系統不對外發送 Webhook。** 跨 BC 通訊走內部 Domain Event（Transactional Outbox / `event_publication`），非 HTTP Webhook。

### 8.1 Phase B — 金流 Inbound Webhook（預留）

Phase B 對接真金流時，由金流商 callback 本系統 `POST /deposit/webhook`（內部，非本契約公開端點），確認後發 `DepositConfirmed` 事件 credit 錢包（EDD §2.1 / §4.6.1）。

### 8.2 簽名驗證（HMAC-SHA256，Phase B）

```http
X-Signature: sha256=<HMAC-SHA256(secret, raw_body)>
X-Webhook-Timestamp: 1793664000
```

**驗證代碼（Python 範例）：**
```python
import hmac, hashlib, time

def verify_webhook(raw_body: bytes, signature: str, timestamp: str, secret: str) -> bool:
    if abs(time.time() - int(timestamp)) > 300:  # 5 分鐘容許誤差
        return False
    expected = "sha256=" + hmac.new(secret.encode(), f"{timestamp}.".encode() + raw_body, hashlib.sha256).hexdigest()
    return hmac.compare_digest(expected, signature)
```

### 8.3 重試策略（Phase B）

指數退避：1m → 5m → 30m → 2h → 1d；超過 5 次失敗 → 停用並告警。重試保持相同 `event_id`（冪等消費）。

---

## 9. Batch Operations

> **Phase A：本系統無批次端點。** 所有寫入為單筆操作（單帳號資料量小）。此章節保留 Phase B 擴充規格。

### 9.1 Phase B 批次設計（預留）

若未來需批次上傳記錄 / 權益：`POST /records/batch`，回應 `207 Multi-Status`，單次最多 100 筆，採 Partial Success（部分失敗不影響其餘）。

```json
{
  "succeeded": 2,
  "failed": 1,
  "results": [
    { "index": 0, "status": 200 },
    { "index": 1, "status": 200 },
    { "index": 2, "status": 400, "error": "type 不正確" }
  ]
}
```

---

## 10. File Upload

> **本系統無檔案上傳端點。** 存檔（`PUT /save`）以 JSON body 傳輸（上限 4MB，`REQUEST_BODY_MAX`），非 multipart 檔案。

### 10.1 Phase B 大型存檔（預留）

若存檔超過 4MB，Phase B 改採 S3 presigned URL 模式：`POST /save/upload-url` 取得 presigned PUT URL，client 直傳物件儲存；允許格式 `application/json`（gzip 壓縮），單檔上限 50MB。

---

## 11. API Paradigm Decision（API 範式決策）

| 評估維度 | REST | GraphQL | gRPC |
|---------|:----:|:-------:|:----:|
| 瀏覽器 / 遊戲前端客戶端 | ✅ 最佳 | ✅ 良好 | ❌ 受限 |
| 低頻信任層請求（登入/儲值/存檔/上榜） | ✅ 最佳 | ⚠️ 過度設計 | ⚠️ 過度設計 |
| 與現有 Node/TS 原型一致性 | ✅ 一對一對應 | ❌ 需重寫 | ❌ 需重寫 |
| HTTP 原生快取 / CDN（公開排行） | ✅ 原生 | ⚠️ 需自訂 | ❌ 需自訂 |
| 型別安全 + 程式碼生成 | ⚠️ OpenAPI | ⚠️ SDL | ✅ 最佳 |
| 生態系成熟度（Spring Boot） | ✅ 最成熟 | ✅ 成熟 | ✅ 成熟 |

**本產品決策：REST + JSON（over HTTPS）。**

**決策依據：** yoCat Office 信任層請求頻率低（非每幀，以登入/儲值/存檔/上榜為主，CONSTANTS §8），資源邊界清晰（7 BC 對應明確資源），且需與現有 Node/TS 原型（accounts.ts 14 端點）一對一對應以降低遷移風險。REST 的 HTTP 原生快取利於公開排行查詢（`GET /leaderboard`），生態系與 Spring Boot 3.3 整合最成熟。GraphQL（靈活查詢）與 gRPC（服務間高效）對本低頻 C 端場景屬過度設計。

**備選考量：** 跨 BC 內部通訊已採 Domain Event（Outbox）而非同步 RPC；Phase B 若引入獨立微服務，內部可評估 gRPC。即時功能（本系統目前無）未來以 SSE 補充。

---

## 12. OpenAPI 3.1 Specification Example（規格範本）

> 完整規格維護於 [`docs/openapi.yaml`](openapi.yaml)。以下為代表性端點（member / wallet）的標準規格，所有端點遵循此結構。

```yaml
openapi: "3.1.0"
info:
  title: "yoCat Office 後端信任層 API"
  version: "1.0.0"
  description: "yocat-office-backend：member/wallet/deposit/entitlement/record/leaderboard/game-save 七有界上下文信任層 API"
  contact:
    name: "Backend Core（聖祐遊戲）"
    email: "boss@yocat.example"
  license:
    name: "Proprietary"

servers:
  - url: "https://api.yocat.sayyogames.com"
    description: "Production"
  - url: "https://api-staging.yocat.sayyogames.com"
    description: "Staging"
  - url: "http://localhost:8080"
    description: "Development"

security:
  - BearerAuth: []

paths:
  /auth/register:
    post:
      summary: "註冊新帳號"
      operationId: "register"
      tags: ["member"]
      security: []
      requestBody:
        required: true
        content:
          application/json:
            schema: { $ref: "#/components/schemas/RegisterRequest" }
      responses:
        "200":
          description: "註冊成功，簽發 session"
          content:
            application/json:
              schema: { $ref: "#/components/schemas/AuthResponse" }
        "400": { $ref: "#/components/responses/ValidationError" }
        "409": { $ref: "#/components/responses/Conflict" }
        "429": { $ref: "#/components/responses/TooManyRequests" }

  /auth/login:
    post:
      summary: "登入"
      operationId: "login"
      tags: ["member"]
      security: []
      requestBody:
        required: true
        content:
          application/json:
            schema: { $ref: "#/components/schemas/LoginRequest" }
      responses:
        "200":
          description: "登入成功"
          content:
            application/json:
              schema: { $ref: "#/components/schemas/AuthResponse" }
        "401": { $ref: "#/components/responses/Unauthorized" }
        "429": { $ref: "#/components/responses/TooManyRequests" }

  /wallet/spend:
    post:
      summary: "花費（行鎖扣款）"
      operationId: "spend"
      tags: ["wallet"]
      requestBody:
        required: true
        content:
          application/json:
            schema: { $ref: "#/components/schemas/SpendRequest" }
      responses:
        "200":
          description: "扣款成功"
          content:
            application/json:
              schema: { $ref: "#/components/schemas/WalletOpResponse" }
        "400": { $ref: "#/components/responses/ValidationError" }
        "401": { $ref: "#/components/responses/Unauthorized" }
        "402":
          description: "餘額不足"
          content:
            application/json:
              schema: { $ref: "#/components/schemas/PaymentRequiredResponse" }
        "429": { $ref: "#/components/responses/TooManyRequests" }

  /leaderboard:
    get:
      summary: "公開排行 Top N"
      operationId: "leaderboardTop"
      tags: ["leaderboard"]
      security: []
      parameters:
        - name: mode
          in: query
          schema: { type: string, default: "" }
        - name: limit
          in: query
          schema: { type: integer, minimum: 1, maximum: 100, default: 20 }
      responses:
        "200":
          description: "Top N"
          content:
            application/json:
              schema: { $ref: "#/components/schemas/LeaderboardResponse" }
        "429": { $ref: "#/components/responses/TooManyRequests" }

components:
  securitySchemes:
    BearerAuth:
      type: http
      scheme: bearer
      bearerFormat: opaque
      description: "opaque session token（32B hex，TTL 30 天）"

  schemas:
    RegisterRequest:
      type: object
      required: [email, displayName, password]
      properties:
        email: { type: string, format: email, example: "boss@yocat.example" }
        displayName: { type: string, minLength: 1, maxLength: 24, example: "喵喵科技" }
        password: { type: string, minLength: 6, example: "s3cret!" }
    LoginRequest:
      type: object
      required: [email, password]
      properties:
        email: { type: string, format: email }
        password: { type: string }
    AccountView:
      type: object
      required: [id, email, display_name]
      properties:
        id: { type: string, example: "1024" }
        email: { type: string, format: email }
        display_name: { type: string }
    WalletView:
      type: object
      required: [cash, gems]
      properties:
        cash: { type: integer, format: int64, example: 18750 }
        gems: { type: integer, format: int64, example: 330 }
    AuthResponse:
      type: object
      required: [token, account, wallet]
      properties:
        token: { type: string, example: "3f8c1d2ae9b7..." }
        account: { $ref: "#/components/schemas/AccountView" }
        wallet: { $ref: "#/components/schemas/WalletView" }
    SpendRequest:
      type: object
      required: [currency, amount]
      properties:
        currency: { type: string, enum: [cash, gems] }
        amount: { type: integer, format: int64, minimum: 1, example: 1250 }
        reason: { type: string, maxLength: 40, nullable: true }
    WalletOpResponse:
      type: object
      required: [ok, currency, balance, wallet]
      properties:
        ok: { type: boolean, example: true }
        currency: { type: string, enum: [cash, gems] }
        balance: { type: integer, format: int64 }
        wallet: { $ref: "#/components/schemas/WalletView" }
    PaymentRequiredResponse:
      type: object
      required: [error, wallet]
      properties:
        error: { type: string, example: "餘額不足" }
        wallet: { $ref: "#/components/schemas/WalletView" }
    LeaderboardEntry:
      type: object
      required: [runId, name, mode, score, updatedAt]
      properties:
        runId: { type: string }
        name: { type: string }
        mode: { type: string }
        score: { type: integer, format: int64 }
        grade: { type: string, nullable: true }
        updatedAt: { type: string, format: date-time }
    LeaderboardResponse:
      type: object
      required: [leaderboard]
      properties:
        leaderboard:
          type: array
          items: { $ref: "#/components/schemas/LeaderboardEntry" }
    ErrorResponse:
      type: object
      required: [error]
      properties:
        error: { type: string }

  responses:
    Unauthorized:
      description: "未認證或 token 無效"
      content:
        application/json:
          schema: { $ref: "#/components/schemas/ErrorResponse" }
          example: { error: "請先登入" }
    ValidationError:
      description: "請求參數驗證失敗"
      content:
        application/json:
          schema: { $ref: "#/components/schemas/ErrorResponse" }
          example: { error: "金額不正確" }
    NotFound:
      description: "找不到路由"
      content:
        application/json:
          schema: { $ref: "#/components/schemas/ErrorResponse" }
          example: { error: "api not found" }
    Conflict:
      description: "資源衝突（已存在/已擁有）"
      content:
        application/json:
          schema: { $ref: "#/components/schemas/ErrorResponse" }
          example: { error: "已經擁有這個項目" }
    TooManyRequests:
      description: "超過速率限制"
      headers:
        Retry-After: { schema: { type: integer } }
        X-RateLimit-Limit: { schema: { type: integer } }
        X-RateLimit-Remaining: { schema: { type: integer } }
      content:
        application/json:
          schema: { $ref: "#/components/schemas/ErrorResponse" }
          example: { error: "請求過於頻繁" }
```

---

## 13. API Changelog

| 版本 | 日期 | Breaking Changes | 新功能 | 棄用（Deprecated） |
|------|------|-----------------|--------|-------------------|
| v1.0 | 2026-10-30 | — | 初始版本：16 業務端點（+2 健康檢查），7 有界上下文；opaque session 認證；`GET /deposit/packs`、`POST /deposit/checkout`（deposit BC）新增 | — |
| v1.1 | — | — | （Phase B 預留：真金流 Webhook、批次、S3 存檔） | — |

> Breaking Change 定義：移除欄位、更改欄位類型、更改 HTTP method、更改端點路徑、更改認證機制。

---

## 14. API Review Checklist

### 命名與結構
- [x] URL 使用名詞資源（`/entitlements`、`/records`、`/leaderboard`）
- [x] Request/Response 欄位命名一致（原型 wire 格式，camelCase for itemId/runId/updatedAt）
- [x] 時間欄位統一 ISO 8601 UTC（`*At` 後綴）

### Response 一致性
- [x] 錯誤回應統一 `{ "error": "..." }`（402 附 wallet）
- [x] 列表端點（`/records`、`/leaderboard`）有排序 + limit 夾擠策略
- [x] 所有端點回應碼完整（成功 2XX + 500）

### 安全性
- [x] 所有非公開端點要求 Bearer session token
- [x] `account_id` 一律取自 session，不信任 client 傳入（防越權）
- [x] 認證端點有嚴格 Rate Limiting（login 10/min、register 5/min）
- [x] 密碼 scrypt + timingSafeEqual；回應不洩露 stack trace / SQL
- [x] 狀態變更端點冪等標注完整（§7）

### 版本與相容性
- [x] Breaking change 有版本升號計畫（§15）
- [x] OpenAPI spec（§12 / `docs/openapi.yaml`）已同步

### 文件完整性
- [x] 每個端點有完整 Request/Response 範例 + Java DTO
- [x] 所有錯誤碼收錄於 §5.2 Registry
- [x] Rate Limiting 規則於 §6 明確
- [x] 跨 BC 通訊（Domain Event）於 §1.3 / §8 說明

### HA / Resilience（必查，所有端點）
- [x] **Timeout**：API 請求逾時 3s（DB 查詢逾時 2s）；§2.2 / §6 定義，非無限
- [x] **Retry**：client 對 429/503 採指數退避 + jitter（最大 3 次）；Outbox 事件最大 5 次重試（§1.3.4）
- [x] **冪等**：重試端點冪等性已標注（§7；POST /deposit/checkout 需 Idempotency-Key）
- [x] **Rate Limit CB**：429/503 含 Retry-After，client 不盲目 retry（§6.3）
- [x] **Circuit Breaker**：DB/Redis 關鍵呼叫有 CB，open 時回 503 降級（§1.3.2、EDD §3.6）
- [x] **Graceful Shutdown**：pod 終止前 in-flight request ≤ 30s 完成才終止（§2.8.4、EDD §3.6.5）
- [x] **Event Consumer 冪等性**：Domain Event（`MemberRegistered`/`DepositConfirmed`/`WalletBalanceChanged`/`EntitlementGranted`）消費前先查 `event_id`（`event_publication` 完成標記 + `SELECT FOR UPDATE SKIP LOCKED`），已處理則直接 ACK 不重複執行業務（At-least-once 投遞下不重複入帳，EDD §3.6.2 / §4.6）

### Bounded Context 封裝（Spring Modulith HC-2，必查）
- [x] Document Control「Owning BC / Service」= yocat-office-backend（具體服務名，非 placeholder）
- [x] §1.1 Service Encapsulation 原則已確認
- [x] API Response 經 DTO/View Model 層，與 DB 欄位解耦
- [x] 無端點依賴其他 BC 的 DB Table（跨 BC 走 Public API `WalletService.debit()` 或 Domain Event）
- [x] 冷啟動測試：其他 BC 以 stub 取代時本 API 端點仍可返回 2XX

---

## 15. API Versioning & Deprecation Policy

### 15.1 版本策略

| 策略 | 說明 |
|------|------|
| 版本格式 | 目前 v1 隱含於路徑（`/auth/*` 等，與原型一致）；破壞性變更時引入 `/v2/` 前綴 |
| 並行版本數 | 最多同時維護 2 個主版本（Current + Previous） |
| Breaking Change 定義 | 移除欄位、改變型別、移除 Endpoint、改變 HTTP Method、更改認證機制 |
| Non-Breaking Change | 新增可選欄位、新增 Endpoint、新增 HTTP Status Code |
| 向下相容保證 | 主版號升級後舊版維護期 ≥ 180 天（棄用通知 ≥ 90 天） |

### 15.2 Deprecation 流程

```mermaid
stateDiagram-v2
    [*] --> Active : 發佈版本
    Active --> Deprecated : 宣告廢棄（Deprecation Notice）
    Deprecated --> Sunset : 寬限期結束（≥ 6 個月）
    Sunset --> Removed : 正式移除
    Removed --> [*]
```

**Deprecation 通知機制（Response Header）：**
```http
Deprecation: true
Sunset: Sat, 01 Jan 2028 00:00:00 GMT
Link: <https://api.yocat.sayyogames.com/v2/wallet/spend>; rel="successor-version"
Warning: 299 - "This endpoint is deprecated. Please migrate to /v2/"
```

### 15.3 版本遷移指南格式

```markdown
## Migration Guide: v1 → v2
### Breaking Changes
| v1 Endpoint | v2 Endpoint | 變更說明 |
|-------------|-------------|---------|
| `POST /wallet/topup`（Phase A 假付費） | `POST /deposit/checkout`（真金流） | 儲值統一走 deposit BC 冪等訂單 |
### Timeline
- 2027-xx：v2 發佈，v1 進入廢棄期
- +90 天：v1 回應加入 Deprecation headers
- +180 天：v1 正式 Sunset
```

### 15.4 語義化版本

`MAJOR.MINOR.PATCH`：MAJOR=破壞性變更（新版本路徑）；MINOR=向下相容新功能；PATCH=修補不影響行為。

---

## 16. Client SDK & Code Generation

### 16.1 SDK 生成策略

| 語言 | 工具 | 輸出路徑 | 用途 |
|------|------|---------|------|
| TypeScript | `openapi-generator-cli`（typescript-fetch） | `sdk/typescript/` | 遊戲前端客戶端（主要消費者） |
| Java | `openapi-generator-cli`（java，RestClient） | `sdk/java/` | 服務間 / 測試 |

### 16.2 SDK 生成指令

```bash
# 生成 TypeScript SDK（遊戲前端）
npx @openapitools/openapi-generator-cli generate \
  -i docs/openapi.yaml \
  -g typescript-fetch \
  -o sdk/typescript \
  --additional-properties=supportsES6=true,modelPropertyNaming=original

# 生成 Java SDK
npx @openapitools/openapi-generator-cli generate \
  -i docs/openapi.yaml \
  -g java \
  -o sdk/java \
  --additional-properties=library=restclient,useJakartaEe=true

# 驗證 OpenAPI 規格
npx @stoplight/spectral-cli lint docs/openapi.yaml
```

### 16.3 SDK 使用範例（TypeScript）

```typescript
import { Configuration, MemberApi, WalletApi } from "@yocat/office-sdk";

const config = new Configuration({ basePath: "https://api.yocat.sayyogames.com" });
const member = new MemberApi(config);

// 登入
const auth = await member.login({ loginRequest: { email: "boss@yocat.example", password: "s3cret!" } });
localStorage.setItem("token", auth.token);

// 帶 token 花費
const wallet = new WalletApi(new Configuration({ basePath: config.basePath, accessToken: auth.token }));
const res = await wallet.spend({ spendRequest: { currency: "cash", amount: 1250, reason: "salary_day_42" } });
```

---

## 17. API Observability & SLO

### 17.1 標準 Observability Headers

| Header | 方向 | 說明 |
|--------|------|------|
| `X-Request-Id` | Req/Res | 請求唯一 ID（traceparent 傳播），貫穿 log/trace |
| `X-Response-Time` | Res | 伺服器處理時間（ms） |
| `X-RateLimit-*` | Res | 速率限制狀態（§6） |

### 17.2 SLO 定義（CONSTANTS §4）

| SLO 指標 | 目標值 | 測量窗口 | 警報閾值 |
|---------|--------|---------|---------|
| 可用性（Availability） | ≥ 99.9%（< 8.7h/year） | 30 天滾動 | < 99.5% |
| P50 延遲 | < 200ms @ 500 PCU | 5 分鐘 | > 300ms |
| P99 延遲 | < 1000ms @ 500 PCU（成長期 3000 PCU < 1500ms） | 5 分鐘 | > 1500ms |
| 排行 Top N 查詢 P99 | < 300ms（limit ≤ 100） | 5 分鐘 | > 500ms |
| 錯誤率（5xx） | < 1% | 5 分鐘 | > 1%（持續 5 分 → P1） |
| HA 滾動更新錯誤率 | < 0.1% | 滾動更新期間 | > 0.1% |
| 寫入交易吞吐量 | ≥ 200 txn/sec | 壓測 | < 200 |
| 錢包對帳一致性 | 0 筆不一致（ledger 100% 相符） | 併發壓測 | 任一不一致 → P0 |

### 17.3 Error Budget 計算

```
月度錯誤預算 = (1 - SLO可用性) × 月度總請求數
範例（99.9% 可用性，月度 10M 請求）：
  錯誤預算 = 0.001 × 10,000,000 = 10,000 次允許失敗
  每日預算 = 10,000 / 30 ≈ 333 次/天
告警：錯誤預算消耗 > 50%（月中）→ 凍結非必要發佈
```

### 17.4 監測工具

- **Metrics**：Micrometer + Prometheus（`/actuator/prometheus`）。
- **Trace**：OpenTelemetry（採樣 Prod 10% / Staging 100%，錯誤 100%，CONSTANTS §11）。
- **Log**：結構化 JSON log（熱 90 天 / 冷 365 天，CONSTANTS §8）。
- **可用性 / RTO-RPO**：RTO 30 min / RPO 5 min（CONSTANTS §4）。

---

## 18. Admin API（條件章節）

本專案 `has_admin_backend=false`（api-only，C 端信任層），**略過 §18**。營運/客服（OPS 角色）的對帳稽核與強制登出屬 Phase B Admin 後台，將於後台專案獨立設計，不在本 C 端 API 契約範圍。

---

*文件維護者：Backend Core（聖祐遊戲）| 上游：EDD §5.2 / CONSTANTS（SSOT）| 最後更新：2026-10-30*
