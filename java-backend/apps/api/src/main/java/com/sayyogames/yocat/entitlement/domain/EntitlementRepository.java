package com.sayyogames.yocat.entitlement.domain;

import com.sayyogames.yocat.shared.AccountId;
import java.util.List;

/**
 * &lt;&lt;Repository&gt;&gt; port for the {@link Entitlement} aggregate (entitlement BC).
 *
 * <p>Domain-owned interface; infrastructure adapter injected via Spring DI (DIP).</p>
 */
public interface EntitlementRepository {

    /**
     * True when the account already owns {@code (kind, itemId)} — the idempotent-buy guard that maps to 409
     * (SCHEMA {@code UNIQUE(account_id,kind,item_id)}). Caller: BuyEntitlementUseCase (EDD §4.5.4.3).
     */
    boolean existsOwned(AccountId accountId, EntitlementKind kind, String itemId);

    /** List the account's owned entitlements. Caller: ListEntitlementsUseCase (GET /entitlements, API §956). */
    List<Entitlement> findByAccount(AccountId accountId);

    /**
     * Persist a granted entitlement (INSERT). Caller: BuyEntitlementUseCase (EDD §4.5.4.3).
     * May propagate the uniqueness violation that enforces 409.
     */
    Entitlement save(Entitlement entitlement);
}
