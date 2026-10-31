---
diagram: class-application
uml-type: Class Diagram（類別圖 — Application Layer）
source: docs/EDD.md §4.5.2 (2)
generated: 2026-10-30T00:00:00Z
---

# Class Diagram — Application Layer

> 來源：EDD.md §4.5.2（2）class-application

```mermaid
classDiagram
    class SpendWalletUseCase {
        <<UseCase>>
        -walletRepo: WalletRepository
        -publisher: DomainEventPublisher
        +execute(cmd: SpendCommand) WalletView
    }
    class TopUpWalletUseCase {
        <<UseCase>>
        -walletRepo: WalletRepository
        -publisher: DomainEventPublisher
        +execute(cmd: TopUpCommand) WalletView
    }
    class BuyEntitlementUseCase {
        <<UseCase>>
        -entRepo: EntitlementRepository
        -walletService: WalletService
        +execute(cmd: BuyCommand) BuyResult
    }
    class RegisterMemberUseCase {
        <<UseCase>>
        -memberRepo: MemberRepository
        -passwordEncoder: PasswordEncoder
        -publisher: DomainEventPublisher
        +execute(cmd: RegisterCommand) RegisterResult
    }
    class WalletService {
        <<ApplicationService>>
        +topUp(id: AccountId, c: Currency, amt: long, reason: String) WalletView
        +debit(id: AccountId, c: Currency, amt: long, reason: String) WalletView
    }
    class DomainEventPublisher {
        <<interface>>
        <<Port>>
        +publish(event: DomainEvent) void
    }
    class SpendCommand {
        <<DTO>>
        +accountId: AccountId
        +currency: Currency
        +amount: long
        +reason: String
    }
    class WalletView {
        <<DTO>>
        +cash: long
        +gems: long
    }

    SpendWalletUseCase --> "1" WalletRepository : uses
    SpendWalletUseCase --> "1" DomainEventPublisher : publishes
    TopUpWalletUseCase --> "1" WalletRepository : uses
    BuyEntitlementUseCase --> "1" WalletService : debits via
    RegisterMemberUseCase --> "1" MemberRepository : uses
    RegisterMemberUseCase --> "1" DomainEventPublisher : publishes
    SpendWalletUseCase ..> SpendCommand : accepts
    SpendWalletUseCase ..> WalletView : returns
    WalletService ..> SpendWalletUseCase : delegates
```

## 技術說明

Application Layer 以 UseCase（每個對應一條 PRD AC）編排 Domain 物件與 Repository，透過 `<<Port>>`（`DomainEventPublisher`）與外部解耦。`WalletService` 為跨 UseCase 的 ApplicationService，提供 wallet BC 的 Public API 供 entitlement BC 同交易呼叫。所有輸入/輸出以 `<<DTO>>`（`SpendCommand`、`WalletView`）界定，不外洩 Domain 內部型別。

## 白話說明

這層是「做事的流程」：收到儲值、花費、購買、註冊的請求後，照順序呼叫錢包與會員的規則，把結果整理好回傳。
