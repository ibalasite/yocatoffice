package com.sayyogames.yocat.entitlement.domain;

import com.sayyogames.yocat.shared.AccountId;
import com.sayyogames.yocat.shared.DomainEvent;

import java.time.Instant;
import java.util.Objects;

/**
 * &lt;&lt;DomainEvent&gt;&gt; — owned by the entitlement BC (EDD §4.6.1 {@code entitlement.item.granted} v1).
 * Published after an item is granted (EDD §4.5.4.3 buy happy path); BC-internal, not consumed across
 * BCs (EDD §4.6.1 Consumer BC(s)). Pure domain — no Spring, no JDBC.
 *
 * @param accountId  the granted account's identity
 * @param kind       the entitlement kind granted
 * @param itemId     the granted item identifier
 * @param occurredAt the instant the grant committed
 */
public record EntitlementGranted(
        AccountId accountId,
        EntitlementKind kind,
        String itemId,
        Instant occurredAt) implements DomainEvent {

    public EntitlementGranted {
        Objects.requireNonNull(accountId, "accountId 不可為 null");
        Objects.requireNonNull(kind, "kind 不可為 null");
        Objects.requireNonNull(itemId, "itemId 不可為 null");
        Objects.requireNonNull(occurredAt, "occurredAt 不可為 null");
    }

    /** Raises the event stamped at the current instant. */
    public EntitlementGranted(AccountId accountId, EntitlementKind kind, String itemId) {
        this(accountId, kind, itemId, Instant.now());
    }
}
