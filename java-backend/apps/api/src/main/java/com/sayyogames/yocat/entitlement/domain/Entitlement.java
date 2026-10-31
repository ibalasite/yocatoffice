package com.sayyogames.yocat.entitlement.domain;

import com.sayyogames.yocat.shared.AccountId;
import java.time.Instant;

/**
 * &lt;&lt;AggregateRoot&gt;&gt; — entitlement BC owned item (EDD §3.4 / §4.5.4.3, SCHEMA §3.4.1
 * {@code entitlement.entitlements}, {@code UNIQUE(account_id, kind, item_id) WHERE deleted_at IS NULL}).
 * Pure domain — no Spring, no JDBC.
 *
 * <p>Fields mirror the SCHEMA columns {@code id / account_id / kind / item_id / acquired_at}. A not-yet
 * persisted grant ({@link #grant}) carries {@code id == 0}; the repository assigns the {@code BIGSERIAL}
 * identity on {@code save}. A persisted grant is rehydrated through {@link #of}.</p>
 */
public class Entitlement {

    private long id;
    private AccountId accountId;
    private EntitlementKind kind;
    private String itemId;
    private Instant acquiredAt;

    public Entitlement() {
    }

    private Entitlement(long id, AccountId accountId, EntitlementKind kind, String itemId, Instant acquiredAt) {
        this.id = id;
        this.accountId = accountId;
        this.kind = kind;
        this.itemId = itemId;
        this.acquiredAt = acquiredAt;
    }

    /** A new, not-yet-persisted grant ({@code id} assigned by the repository on save). Caller: BuyEntitlementUseCase. */
    public static Entitlement grant(AccountId accountId, EntitlementKind kind, String itemId) {
        return new Entitlement(0L, accountId, kind, itemId, null);
    }

    /** Reconstitutes a persisted entitlement from its columns (repository rehydration, SCHEMA §3.4.1). */
    public static Entitlement of(long id, AccountId accountId, EntitlementKind kind, String itemId, Instant acquiredAt) {
        return new Entitlement(id, accountId, kind, itemId, acquiredAt);
    }

    public long id() {
        return id;
    }

    public AccountId accountId() {
        return accountId;
    }

    public EntitlementKind kind() {
        return kind;
    }

    public String itemId() {
        return itemId;
    }

    public Instant acquiredAt() {
        return acquiredAt;
    }
}
