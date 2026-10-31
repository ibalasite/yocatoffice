package com.sayyogames.yocat.wallet.domain;

import com.sayyogames.yocat.shared.AccountId;
import com.sayyogames.yocat.shared.InvalidAmountException;

/**
 * &lt;&lt;AggregateRoot&gt;&gt; — wallet BC (EDD §4.5.2 class-domain, §4.5.6 state-machine-wallet,
 * §4.7 Algorithm 1). Server-authoritative balances per currency. Pure domain — no Spring, no JDBC.
 *
 * <p>Invariants (EDD §4.5.2 Invariant Table): {@code cash >= 0}, {@code gems >= 0}, and every
 * mutation appends a {@link LedgerEntry} whose post-mutation {@code balance} reconciles with the
 * opening balance plus the signed {@code delta} (Σdelta = balance). A spend amount must lie within
 * CONSTANTS §8 [TOPUP_AMOUNT_MIN=1, TOPUP_AMOUNT_MAX=10_000_000].
 */
public class Wallet {

    private static final long AMOUNT_MIN = 1L;
    private static final long AMOUNT_MAX = 10_000_000L;

    private AccountId accountId;
    private long cash;
    private long gems;

    protected Wallet() {
    }

    private Wallet(AccountId accountId, long cash, long gems) {
        this.accountId = accountId;
        this.cash = cash;
        this.gems = gems;
    }

    /** Reconstitutes a wallet with known balances (repository rehydration). */
    public static Wallet of(AccountId accountId, long cash, long gems) {
        return new Wallet(accountId, cash, gems);
    }

    public AccountId accountId() {
        return accountId;
    }

    /** Current balance of the given currency (always &gt;= 0). */
    public long balanceOf(Currency currency) {
        return currency == Currency.GEMS ? gems : cash;
    }

    /**
     * Debits {@code amount} of {@code currency} (EDD §4.7 Algorithm 1). Rejects an amount outside
     * [1, 10_000_000] with {@link InvalidAmountException} (→ 400) and a balance below the amount with
     * {@link InsufficientBalanceException} (→ 402); in both rejection cases the balance is left
     * unchanged and no ledger line is produced. On success lowers the balance and returns the
     * negative-delta ledger line recording the post-mutation balance.
     */
    public LedgerEntry debit(Currency currency, long amount) {
        requireValidAmount(amount);
        long balance = balanceOf(currency);
        if (balance < amount) {
            throw new InsufficientBalanceException(balance);
        }
        long newBalance = balance - amount;
        setBalance(currency, newBalance);
        return LedgerEntry.of(accountId, currency, -amount, LedgerReason.BUY_COSMETIC, newBalance);
    }

    /**
     * Credits (tops up) {@code amount} of {@code currency}. Rejects an amount outside
     * [1, 10_000_000] with {@link InvalidAmountException} (→ 400). On success raises the balance and
     * returns the positive-delta ledger line with reason {@link LedgerReason#TOPUP}.
     */
    public LedgerEntry credit(Currency currency, long amount) {
        requireValidAmount(amount);
        long newBalance = balanceOf(currency) + amount;
        setBalance(currency, newBalance);
        return LedgerEntry.of(accountId, currency, amount, LedgerReason.TOPUP, newBalance);
    }

    private static void requireValidAmount(long amount) {
        if (amount < AMOUNT_MIN || amount > AMOUNT_MAX) {
            throw new InvalidAmountException(amount);
        }
    }

    private void setBalance(Currency currency, long newBalance) {
        if (currency == Currency.GEMS) {
            this.gems = newBalance;
        } else {
            this.cash = newBalance;
        }
    }
}
