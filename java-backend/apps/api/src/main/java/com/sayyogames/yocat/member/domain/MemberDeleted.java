package com.sayyogames.yocat.member.domain;

import com.sayyogames.yocat.shared.AccountId;
import com.sayyogames.yocat.shared.DomainEvent;

import java.time.Instant;
import java.util.Objects;

/**
 * &lt;&lt;DomainEvent&gt;&gt; — owned by the member BC (EDD §4.6.1 {@code member.account.deleted} v1).
 * Published when an account is deleted; consumed by the wallet, entitlement, record, leaderboard
 * and game-save BCs to purge the member's data (EDD §3.4, §4.6.1 Consumer BC(s)). Pure domain —
 * no Spring, no JDBC.
 *
 * @param accountId  the deleted account's identity
 * @param occurredAt the instant the deletion committed
 */
public record MemberDeleted(AccountId accountId, Instant occurredAt) implements DomainEvent {

    public MemberDeleted {
        Objects.requireNonNull(accountId, "accountId 不可為 null");
        Objects.requireNonNull(occurredAt, "occurredAt 不可為 null");
    }

    /** Raises the event stamped at the current instant. */
    public MemberDeleted(AccountId accountId) {
        this(accountId, Instant.now());
    }
}
