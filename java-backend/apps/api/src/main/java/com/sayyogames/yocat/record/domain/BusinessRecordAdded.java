package com.sayyogames.yocat.record.domain;

import com.sayyogames.yocat.shared.AccountId;
import com.sayyogames.yocat.shared.DomainEvent;

import java.time.Instant;
import java.util.Objects;

/**
 * &lt;&lt;DomainEvent&gt;&gt; — owned by the record BC (EDD §4.6.1 {@code record.history.added} v1).
 * Published after a {@code records} INSERT; BC-internal, not consumed across BCs (EDD §4.6.1
 * Consumer BC(s)). Pure domain — no Spring, no JDBC.
 *
 * @param accountId  the record owner's identity
 * @param type       the business record type
 * @param occurredAt the instant the record was added
 */
public record BusinessRecordAdded(
        AccountId accountId,
        String type,
        Instant occurredAt) implements DomainEvent {

    public BusinessRecordAdded {
        Objects.requireNonNull(accountId, "accountId 不可為 null");
        Objects.requireNonNull(type, "type 不可為 null");
        Objects.requireNonNull(occurredAt, "occurredAt 不可為 null");
    }

    /** Raises the event stamped at the current instant. */
    public BusinessRecordAdded(AccountId accountId, String type) {
        this(accountId, type, Instant.now());
    }
}
