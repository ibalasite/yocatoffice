package com.sayyogames.yocat.wallet.api;

import com.sayyogames.yocat.shared.AccountId;
import com.sayyogames.yocat.shared.DomainEventPublisher;
import com.sayyogames.yocat.shared.InvalidAmountException;
import com.sayyogames.yocat.wallet.application.WalletView;
import com.sayyogames.yocat.wallet.domain.Currency;
import com.sayyogames.yocat.wallet.domain.LedgerEntry;
import com.sayyogames.yocat.wallet.domain.LedgerReason;
import com.sayyogames.yocat.wallet.domain.Wallet;
import com.sayyogames.yocat.wallet.domain.WalletBalanceChanged;
import com.sayyogames.yocat.wallet.domain.WalletRepository;
import java.util.Locale;

/**
 * &lt;&lt;ApplicationService&gt;&gt; wallet BC Public NamedInterface (EDD §4.1, §4.5.2 class-application).
 *
 * <p>Same-transaction wallet API consumed by the entitlement BC ({@code BuyEntitlementUseCase}) for
 * atomic debit-and-grant, and by the member BC's register listener for the init ledger. Implements the
 * CQRS split (EDD §4.5.2): {@link #topUp} / {@link #debit} are Commands (mutate balance, append ledger,
 * publish {@link WalletBalanceChanged}); {@link #findWallet} is a Query with no side effects. Depends on
 * the domain ports only (DIP — no infrastructure concrete).</p>
 */
public class WalletService {

    /** Legal amount range (CONSTANTS §8 TOPUP_AMOUNT_MIN / TOPUP_AMOUNT_MAX); guarded pre-lock per EDD §4.7 Algorithm 1. */
    private static final long AMOUNT_MIN = 1L;
    private static final long AMOUNT_MAX = 10_000_000L;

    private final WalletRepository walletRepo;
    private final DomainEventPublisher publisher;

    public WalletService(WalletRepository walletRepo, DomainEventPublisher publisher) {
        this.walletRepo = walletRepo;
        this.publisher = publisher;
    }

    /** Credit {@code amount} (EDD §4.1). Command — mutates balance, appends ledger, publishes WalletBalanceChanged. */
    public WalletView topUp(AccountId accountId, Currency currency, long amount, String reason) {
        requireValidAmount(amount);
        Wallet wallet = walletRepo.findForUpdate(accountId, currency)
                .orElseThrow(() -> new IllegalStateException("錢包不存在: " + accountId));
        LedgerReason ledgerReason = LedgerReason.valueOf(reason.toUpperCase(Locale.ROOT));

        wallet.credit(currency, amount);
        long balance = wallet.balanceOf(currency);
        walletRepo.save(wallet);
        walletRepo.appendLedger(LedgerEntry.of(accountId, currency, amount, ledgerReason, balance));
        publisher.publish(new WalletBalanceChanged(accountId, currency, amount, balance, ledgerReason));

        return view(wallet);
    }

    /**
     * Debit {@code amount} under a row lock (EDD §4.1, §4.7 Algorithm 1). Command — mutates balance,
     * appends ledger, publishes event.
     * @throws InvalidAmountException amount ∉ [1, 10_000_000] → 400 (rejected before the lock)
     * @throws com.sayyogames.yocat.wallet.domain.InsufficientBalanceException balance &lt; amount → 402 (no write, no event)
     */
    public WalletView debit(AccountId accountId, Currency currency, long amount, String reason) {
        requireValidAmount(amount);
        Wallet wallet = walletRepo.findForUpdate(accountId, currency)
                .orElseThrow(() -> new IllegalStateException("錢包不存在: " + accountId));
        LedgerReason ledgerReason = LedgerReason.valueOf(reason.toUpperCase(Locale.ROOT));

        wallet.debit(currency, amount); // domain 402 guard + balance mutation
        long balance = wallet.balanceOf(currency);
        walletRepo.save(wallet);
        walletRepo.appendLedger(LedgerEntry.of(accountId, currency, -amount, ledgerReason, balance));
        publisher.publish(new WalletBalanceChanged(accountId, currency, -amount, balance, ledgerReason));

        return view(wallet);
    }

    /** Read the current wallet (EDD §4.1). Query — no side effects (CQRS): no save, no ledger, no event. */
    public WalletView findWallet(AccountId accountId) {
        Wallet wallet = walletRepo.findByAccount(accountId)
                .orElseThrow(() -> new IllegalStateException("錢包不存在: " + accountId));
        return view(wallet);
    }

    private static void requireValidAmount(long amount) {
        if (amount < AMOUNT_MIN || amount > AMOUNT_MAX) {
            throw new InvalidAmountException(amount);
        }
    }

    private static WalletView view(Wallet wallet) {
        return new WalletView(wallet.balanceOf(Currency.CASH), wallet.balanceOf(Currency.GEMS));
    }
}
