---
diagram: class-domain
uml-type: Class Diagram（類別圖 — Domain Layer）
source: docs/EDD.md §4.5.2 (1)
generated: 2026-10-30T00:00:00Z
---

# Class Diagram — Domain Layer（wallet + member 核心）

> 來源：EDD.md §4.5.2（1）class-domain

```mermaid
classDiagram
    class Wallet {
        <<AggregateRoot>>
        -accountId: AccountId
        -cash: Money
        -gems: Money
        -updatedAt: DateTime
        +debit(currency: Currency, amount: long) LedgerEntry
        +credit(currency: Currency, amount: long) LedgerEntry
        +balanceOf(currency: Currency) long
    }
    class LedgerEntry {
        <<Entity>>
        -id: long
        -accountId: AccountId
        -currency: Currency
        -delta: long
        -reason: LedgerReason
        -balance: long
        -meta: JsonMap
        -createdAt: DateTime
    }
    class Money {
        <<ValueObject>>
        -amount: long
        +of(raw: long) Money
        +plus(other: Money) Money
        +minus(other: Money) Money
        +isNegative() Boolean
    }
    class AccountId {
        <<ValueObject>>
        -value: long
        +of(raw: long) AccountId
        +equals(other: AccountId) Boolean
    }
    class Currency {
        <<enumeration>>
        CASH
        GEMS
    }
    class LedgerReason {
        <<enumeration>>
        INIT
        TOPUP
        BUY_COSMETIC
        BUY_DREAM
        BUY_SCENARIO
        RESCUE
    }
    class Account {
        <<AggregateRoot>>
        -id: AccountId
        -email: Email
        -displayName: DisplayName
        -passwordHash: String
        -createdAt: DateTime
        +verifyPassword(raw: String) Boolean
    }
    class Session {
        <<Entity>>
        -token: String
        -accountId: AccountId
        -expiresAt: DateTime
        +isExpired(now: DateTime) Boolean
    }
    class Email {
        <<ValueObject>>
        -value: String
        +of(raw: String) Email
    }
    class DisplayName {
        <<ValueObject>>
        -value: String
        +of(raw: String) DisplayName
    }
    class WalletRepository {
        <<interface>>
        <<Repository>>
        +findForUpdate(id: AccountId, c: Currency) Wallet
        +findByAccount(id: AccountId) Wallet
        +save(w: Wallet) void
        +appendLedger(e: LedgerEntry) void
    }
    class MemberRepository {
        <<interface>>
        <<Repository>>
        +findByEmail(email: Email) Account
        +save(a: Account) AccountId
        +existsByEmail(email: Email) Boolean
    }
    class WalletBalanceChanged {
        <<DomainEvent>>
        +accountId: AccountId
        +currency: Currency
        +delta: long
        +balance: long
        +reason: LedgerReason
        +occurredAt: DateTime
    }
    class MemberRegistered {
        <<DomainEvent>>
        +accountId: AccountId
        +email: Email
        +occurredAt: DateTime
    }

    Wallet *-- "2" Money : holds
    Wallet o-- "1" AccountId : identifies
    Wallet "1" --> "0..*" LedgerEntry : appends
    LedgerEntry o-- "1" Currency : of
    LedgerEntry o-- "1" LedgerReason : because
    Account o-- "1" AccountId : id
    Account *-- "1" Email : email
    Account *-- "1" DisplayName : name
    Account "1" --> "0..*" Session : issues
    WalletRepository ..> Wallet : manages
    MemberRepository ..> Account : manages
    Wallet ..> WalletBalanceChanged : emits
    Account ..> MemberRegistered : emits
```

## 技術說明

Domain Layer 封裝業務不變量與領域邏輯，不依賴任何 Infrastructure 具體類別（DIP）。`Wallet`、`Account` 為 AggregateRoot，負責維護餘額 `>= 0`、email 唯一等 invariant；`WalletRepository`、`MemberRepository` 為抽象介面（`<<Repository>>`），由 Infrastructure 層實作。Enum（`Currency`、`LedgerReason`）與 ValueObject（`Money`、`AccountId`、`Email`、`DisplayName`）皆不可變。`<<DomainEvent>>`（`WalletBalanceChanged`、`MemberRegistered`）對應 EDD §4.6 Domain Events 表。

## 白話說明

這層描述「錢包」與「會員」的核心規則：錢包餘額永遠不能是負的，買東西要先扣錢；每個帳號 email 不能重複。它只管規則，不管資料怎麼存進資料庫。
