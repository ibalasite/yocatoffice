package com.sayyogames.yocat.wallet.domain;

import com.sayyogames.yocat.shared.AccountId;
import java.time.Instant;

/**
 * &lt;&lt;Entity&gt;&gt; — an append-only ledger line inside the Wallet aggregate (EDD §4.5.2
 * class-domain, Invariant Table). Pure domain — no Spring, no JDBC.
 *
 * <p>Immutable once created and produced only by {@link Wallet#debit} / {@link Wallet#credit}. It
 * records the signed {@code delta} applied, the {@code reason}, and the resulting post-mutation
 * {@code balance} (the Σdelta = balance reconciliation invariant). The persistent {@code id} is
 * assigned by the repository on append.
 */
public class LedgerEntry {

    private long id;
    private final AccountId accountId;
    private final Currency currency;
    private final long delta;
    private final LedgerReason reason;
    private final long balance;
    private final Instant createdAt;

    private LedgerEntry(AccountId accountId, Currency currency, long delta, LedgerReason reason, long balance) {
        this.accountId = accountId;
        this.currency = currency;
        this.delta = delta;
        this.reason = reason;
        this.balance = balance;
        this.createdAt = Instant.now();
    }

    /** Creates a ledger line recording a signed {@code delta} and the resulting {@code balance}. */
    public static LedgerEntry of(AccountId accountId, Currency currency, long delta, LedgerReason reason, long balance) {
        return new LedgerEntry(accountId, currency, delta, reason, balance);
    }

    public long id() {
        return id;
    }

    public AccountId accountId() {
        return accountId;
    }

    public Currency currency() {
        return currency;
    }

    public long delta() {
        return delta;
    }

    public LedgerReason reason() {
        return reason;
    }

    public long balance() {
        return balance;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
