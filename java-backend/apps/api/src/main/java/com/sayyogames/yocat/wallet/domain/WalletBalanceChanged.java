package com.sayyogames.yocat.wallet.domain;

import com.sayyogames.yocat.shared.AccountId;
import com.sayyogames.yocat.shared.DomainEvent;

import java.time.Instant;
import java.util.Objects;

/**
 * &lt;&lt;DomainEvent&gt;&gt; — owned by the wallet BC (EDD §4.5.2 class-domain, §4.6.1
 * {@code wallet.balance.changed} v1). Published after a top-up/debit commits (EDD §4.7 Algorithm 1);
 * consumed BC-internally by the record BC for analytics/audit (EDD §4.6.1 Consumer BC(s)).
 * Pure domain — no Spring, no JDBC.
 *
 * @param accountId  the wallet owner's identity
 * @param currency   the currency whose balance changed
 * @param delta      the signed change applied (negative for a debit, positive for a credit)
 * @param balance    the post-mutation balance of {@code currency} (always &gt;= 0)
 * @param reason     the ledger reason for the movement
 * @param occurredAt the instant the movement committed
 */
public record WalletBalanceChanged(
        AccountId accountId,
        Currency currency,
        long delta,
        long balance,
        LedgerReason reason,
        Instant occurredAt) implements DomainEvent {

    public WalletBalanceChanged {
        Objects.requireNonNull(accountId, "accountId 不可為 null");
        Objects.requireNonNull(currency, "currency 不可為 null");
        Objects.requireNonNull(reason, "reason 不可為 null");
        Objects.requireNonNull(occurredAt, "occurredAt 不可為 null");
    }

    /** Raises the event stamped at the current instant (EDD §4.7 Algorithm 1 publish site). */
    public WalletBalanceChanged(AccountId accountId, Currency currency, long delta, long balance, LedgerReason reason) {
        this(accountId, currency, delta, balance, reason, Instant.now());
    }
}
