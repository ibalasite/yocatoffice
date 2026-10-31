---
diagram: modulith-bc-isolation
uml-type: Spring Modulith BC 邊界隔離圖
source: docs/SCHEMA.md Owning BC + docs/ARCH.md §4
generated: 2026-10-30T00:00:00Z
---

# Spring Modulith — BC 邊界隔離圖

> 來源：SCHEMA.md（各 BC 擁有的 schema/tables）+ ARCH.md §4 服務邊界。
> 每個 BC 獨佔自己的 DB schema（HC-1）；跨 BC 僅以 app-layer `account_id`（ID-only，無 DB FK）邏輯參照（紅色虛線），或透過 Public API（綠色實線）。

```mermaid
graph LR
    subgraph member_bc["member BC"]
        m_accounts["accounts"]
        m_sessions["sessions"]
    end
    subgraph wallet_bc["wallet BC"]
        w_wallets["wallets"]
        w_ledger["wallet_ledger"]
    end
    subgraph deposit_bc["deposit BC"]
        d_pack["deposit_pack"]
        d_order["deposit_order（Phase B）"]
    end
    subgraph entitlement_bc["entitlement BC"]
        e_ent["entitlements"]
        e_cat["entitlement_catalog"]
    end
    subgraph record_bc["record BC"]
        r_records["records"]
    end
    subgraph leaderboard_bc["leaderboard BC"]
        l_lb["leaderboard"]
    end
    subgraph gamesave_bc["game-save BC"]
        g_saves["saves"]
    end

    entitlement_bc ==>|"Public API: WalletService.debit()"| wallet_bc
    w_wallets -.->|"ID-only（no FK）account_id"| m_accounts
    e_ent -.->|"ID-only（no FK）account_id"| m_accounts
    r_records -.->|"ID-only（no FK）account_id"| m_accounts
    l_lb -.->|"ID-only（no FK）account_id"| m_accounts
    g_saves -.->|"ID-only（no FK）account_id"| m_accounts
    d_order -.->|"ID-only（no FK）account_id"| m_accounts

    linkStyle 1,2,3,4,5,6 stroke:#ff0000,stroke-dasharray: 5 5
    linkStyle 0 stroke:#00aa00,stroke-width:2px
```

> **隔離原則**：禁止跨 BC 直接存取他 BC 的 DB table / JOIN；所有跨 BC 資料存取必須透過 Public `@NamedInterface` 或 Domain Event（HC-1/HC-2）。帳號刪除以 `MemberDeleted` 事件驅動各 BC 匿名化/級聯清理。
