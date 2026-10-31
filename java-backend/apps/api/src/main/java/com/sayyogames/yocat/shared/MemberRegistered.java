package com.sayyogames.yocat.shared;

import java.time.Instant;
import java.util.Objects;

/**
 * &lt;&lt;DomainEvent&gt;&gt; member-registered cross-BC contract (EDD §4.6.1 {@code member.account.registered} v1).
 * Published by the member BC after the {@code accounts} INSERT commits; consumed by the wallet BC to provision
 * the initial wallet + INIT ledger (EDD §3.4, §4.5.4.5, §7.1).
 *
 * <p>This event is a <em>shared contract</em> deliberately placed in the shared kernel rather than inside the
 * member module: it is consumed by a different bounded context (wallet), and keeping it in the member module
 * would force the consumer (wallet) to depend on the producer (member) while the producer already depends on
 * the consumer via the aggregated {@code /auth/me} view — a cycle. Co-locating the cross-BC event contract in
 * the shared kernel (alongside {@link DomainEvent}, {@link AccountId}) keeps the module dependency graph a DAG
 * (EDD §3.4 HC-5). The payload carries the email as a plain {@code String} so the shared kernel stays free of
 * any BC's value objects.</p>
 *
 * @param accountId  the newly registered account's identity
 * @param email      the registered account's email (canonical string form)
 * @param occurredAt the instant the registration committed
 */
public record MemberRegistered(AccountId accountId, String email, Instant occurredAt) implements DomainEvent {

    public MemberRegistered {
        Objects.requireNonNull(accountId, "accountId 不可為 null");
        Objects.requireNonNull(email, "email 不可為 null");
        Objects.requireNonNull(occurredAt, "occurredAt 不可為 null");
    }

    /** Raises the event stamped at the current instant. */
    public MemberRegistered(AccountId accountId, String email) {
        this(accountId, email, Instant.now());
    }
}
