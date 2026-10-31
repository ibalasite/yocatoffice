---
generated: 2026-10-30T00:00:00Z
source: docs/diagrams/class-*.md
lang_stack: java
purpose: class-to-test mapping for test-plan RTM
---

# Class Inventory — 類別清單與測試追蹤

> 自動生成自 docs/diagrams/class-*.md（提取自 EDD.md §4.5.2）
> 規則：1 Class → 1 src/ 實作檔 → 1 test 測試檔
> lang_stack=java：src 根 `src/main/java/`，test 命名 `*Test.java`/`*IT.java`，test 根 `src/test/java/`

## 類別清單

| Class | Stereotype | Layer | src 路徑（推斷）| test 路徑（推斷）| TC-ID 前綴 |
|-------|-----------|-------|--------------|--------------|-----------|
| Wallet | AggregateRoot | Domain | `wallet/domain/Wallet.java` | `wallet/domain/WalletTest.java` | TC-UNIT-WALLET |
| LedgerEntry | Entity | Domain | `wallet/domain/LedgerEntry.java` | `wallet/domain/LedgerEntryTest.java` | TC-UNIT-LEDGERENTRY |
| Money | ValueObject | Domain | `shared/Money.java` | `shared/MoneyTest.java` | TC-UNIT-MONEY |
| AccountId | ValueObject | Domain | `shared/AccountId.java` | `shared/AccountIdTest.java` | TC-UNIT-ACCOUNTID |
| Currency | enumeration | Domain | `wallet/domain/Currency.java` | — | TC-UNIT-CURRENCY |
| LedgerReason | enumeration | Domain | `wallet/domain/LedgerReason.java` | — | TC-UNIT-LEDGERREASON |
| Account | AggregateRoot | Domain | `member/domain/Account.java` | `member/domain/AccountTest.java` | TC-UNIT-ACCOUNT |
| Session | Entity | Domain | `member/domain/Session.java` | `member/domain/SessionTest.java` | TC-UNIT-SESSION |
| Email | ValueObject | Domain | `member/domain/Email.java` | `member/domain/EmailTest.java` | TC-UNIT-EMAIL |
| DisplayName | ValueObject | Domain | `member/domain/DisplayName.java` | `member/domain/DisplayNameTest.java` | TC-UNIT-DISPLAYNAME |
| WalletRepository | Repository(interface) | Domain | `wallet/domain/WalletRepository.java` | （合約測試） | TC-UNIT-WALLETREPO |
| MemberRepository | Repository(interface) | Domain | `member/domain/MemberRepository.java` | — | TC-UNIT-MEMBERREPO |
| WalletBalanceChanged | DomainEvent | Domain | `wallet/domain/WalletBalanceChanged.java` | — | TC-UNIT-WALLETBALCHG |
| MemberRegistered | DomainEvent | Domain | `member/domain/MemberRegistered.java` | — | TC-UNIT-MEMBERREG |
| SpendWalletUseCase | UseCase | Application | `wallet/application/SpendWalletUseCase.java` | `wallet/application/SpendWalletUseCaseTest.java` | TC-UNIT-SPENDWALLET |
| TopUpWalletUseCase | UseCase | Application | `wallet/application/TopUpWalletUseCase.java` | `wallet/application/TopUpWalletUseCaseTest.java` | TC-UNIT-TOPUPWALLET |
| BuyEntitlementUseCase | UseCase | Application | `entitlement/application/BuyEntitlementUseCase.java` | `entitlement/application/BuyEntitlementUseCaseTest.java` | TC-UNIT-BUYENT |
| RegisterMemberUseCase | UseCase | Application | `member/application/RegisterMemberUseCase.java` | `member/application/RegisterMemberUseCaseTest.java` | TC-UNIT-REGMEMBER |
| WalletService | ApplicationService | Application | `wallet/api/WalletService.java` | `wallet/api/WalletServiceTest.java` | TC-UNIT-WALLETSVC |
| DomainEventPublisher | Port(interface) | Application | `shared/DomainEventPublisher.java` | — | TC-UNIT-EVENTPUB |
| WalletController | Controller | Presentation | `wallet/api/WalletController.java` | `wallet/api/WalletControllerTest.java` | TC-UNIT-WALLETCTRL |
| AuthController | Controller | Presentation | `member/api/AuthController.java` | `member/api/AuthControllerTest.java` | TC-UNIT-AUTHCTRL |
| JdbcWalletRepository | RepositoryImpl | Infrastructure | `wallet/infra/JdbcWalletRepository.java` | `wallet/infra/JdbcWalletRepositoryIT.java` | TC-UNIT-JDBCWALLETREPO |
| ModulithEventPublisher | Adapter | Infrastructure | `platform/ModulithEventPublisher.java` | — | TC-UNIT-MODULITHPUB |
| ScryptPasswordEncoder | Adapter | Infrastructure | `member/infra/ScryptPasswordEncoder.java` | `member/infra/ScryptPasswordEncoderTest.java` | TC-UNIT-SCRYPTENC |
| BearerTokenFilter | Adapter | Infrastructure | `platform/BearerTokenFilter.java` | `platform/BearerTokenFilterTest.java` | TC-UNIT-BEARERFILTER |

## 測試追蹤統計

| 層次 | Class 數 | 預期 Test File 數（扣除 enum / 無測試項）|
|------|---------|----------------|
| Domain | 14 | 10 |
| Application | 6 | 5 |
| Presentation | 2 | 2 |
| Infrastructure | 4 | 3 |
| **合計** | **26** | **20** |

## 1:1:N 規則提醒

- 每個 Class 對應一個測試檔案（1:1）
- 每個 Public Method 至少 3 個測試案例：S（Success）/ E（Error）/ B（Boundary）（1:N）

## 給 test-plan 的指示

test-plan.md §15.2 Unit Test RTM 應從本表格填充 Class 和 Test File 欄位。
每個 TC-ID 前綴後接 `-S01`, `-E01`, `-B01` 等流水號。
