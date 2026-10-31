package com.sayyogames.yocat.wallet.application;

import com.sayyogames.yocat.shared.DomainEventPublisher;
import com.sayyogames.yocat.shared.InvalidAmountException;
import com.sayyogames.yocat.wallet.domain.Currency;
import com.sayyogames.yocat.wallet.domain.LedgerEntry;
import com.sayyogames.yocat.wallet.domain.LedgerReason;
import com.sayyogames.yocat.wallet.domain.Wallet;
import com.sayyogames.yocat.wallet.domain.WalletBalanceChanged;
import com.sayyogames.yocat.wallet.domain.WalletRepository;
import java.util.Locale;

/**
 * &lt;&lt;UseCase&gt;&gt; concurrency-safe wallet debit (EDD §4.7 Algorithm 1, §4.5.4.1; {@code POST /wallet/spend}).
 *
 * <p>Orchestrates the atomic-debit flow over the domain ports only (DIP — no infrastructure concrete):
 * a fast amount guard (→ 400) runs <em>before</em> the row lock; then the wallet is loaded for update,
 * debited (→ 402 on underflow, leaving no write), the signed ledger line is appended and the
 * {@link WalletBalanceChanged} event is published inside the transaction (Outbox). The command's
 * {@code reason} is resolved to the authoritative {@link LedgerReason} here (EDD §4.7 Algorithm 1 —
 * the application layer assigns the per-command reason).</p>
 */
public class SpendWalletUseCase {

    /** Legal amount range (CONSTANTS §8 TOPUP_AMOUNT_MIN / TOPUP_AMOUNT_MAX); guarded pre-lock per EDD §4.7 Algorithm 1. */
    private static final long AMOUNT_MIN = 1L;
    private static final long AMOUNT_MAX = 10_000_000L;

    private final WalletRepository walletRepo;
    private final DomainEventPublisher publisher;

    public SpendWalletUseCase(WalletRepository walletRepo, DomainEventPublisher publisher) {
        this.walletRepo = walletRepo;
        this.publisher = publisher;
    }

    /**
     * Debit {@code cmd.amount()} of {@code cmd.currency()} from the account wallet under a row lock.
     * @throws InvalidAmountException amount ∉ [1, 10_000_000] → 400 (rejected before the lock is taken)
     * @throws com.sayyogames.yocat.wallet.domain.InsufficientBalanceException balance &lt; amount → 402 (no write, no event)
     */
    public WalletView execute(SpendCommand cmd) {
        if (cmd.amount() < AMOUNT_MIN || cmd.amount() > AMOUNT_MAX) {
            throw new InvalidAmountException(cmd.amount());
        }
        Wallet wallet = walletRepo.findForUpdate(cmd.accountId(), cmd.currency())
                .orElseThrow(() -> new IllegalStateException("錢包不存在: " + cmd.accountId()));
        LedgerReason reason = LedgerReason.valueOf(cmd.reason().toUpperCase(Locale.ROOT));

        wallet.debit(cmd.currency(), cmd.amount()); // domain 402 guard + balance mutation (EDD §4.7 Alg 1)
        long balance = wallet.balanceOf(cmd.currency());
        LedgerEntry ledger = LedgerEntry.of(cmd.accountId(), cmd.currency(), -cmd.amount(), reason, balance);

        walletRepo.save(wallet);
        walletRepo.appendLedger(ledger);
        publisher.publish(new WalletBalanceChanged(cmd.accountId(), cmd.currency(), -cmd.amount(), balance, reason));

        return new WalletView(wallet.balanceOf(Currency.CASH), wallet.balanceOf(Currency.GEMS));
    }
}
