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
 * &lt;&lt;UseCase&gt;&gt; wallet credit / top-up (EDD §4.5.2 class-application, §4.5.4 covering table; {@code POST /wallet/topup}).
 *
 * <p>Credit path over the domain ports only (DIP): validate the amount (→ 400), load the wallet for
 * update, credit it (a credit never underflows, so no 402), append the positive ledger line and publish
 * {@link WalletBalanceChanged} inside the transaction. The command's {@code reason} resolves to the
 * authoritative {@link LedgerReason} here.</p>
 */
public class TopUpWalletUseCase {

    /** Legal amount range (CONSTANTS §8 TOPUP_AMOUNT_MIN / TOPUP_AMOUNT_MAX). */
    private static final long AMOUNT_MIN = 1L;
    private static final long AMOUNT_MAX = 10_000_000L;

    private final WalletRepository walletRepo;
    private final DomainEventPublisher publisher;

    public TopUpWalletUseCase(WalletRepository walletRepo, DomainEventPublisher publisher) {
        this.walletRepo = walletRepo;
        this.publisher = publisher;
    }

    /**
     * Credit {@code cmd.amount()} of {@code cmd.currency()} to the account wallet.
     * @throws InvalidAmountException amount ∉ [1, 10_000_000] → 400 (no write)
     */
    public WalletView execute(TopUpCommand cmd) {
        if (cmd.amount() < AMOUNT_MIN || cmd.amount() > AMOUNT_MAX) {
            throw new InvalidAmountException(cmd.amount());
        }
        Wallet wallet = walletRepo.findForUpdate(cmd.accountId(), cmd.currency())
                .orElseThrow(() -> new IllegalStateException("錢包不存在: " + cmd.accountId()));
        LedgerReason reason = LedgerReason.valueOf(cmd.reason().toUpperCase(Locale.ROOT));

        wallet.credit(cmd.currency(), cmd.amount());
        long balance = wallet.balanceOf(cmd.currency());
        LedgerEntry ledger = LedgerEntry.of(cmd.accountId(), cmd.currency(), cmd.amount(), reason, balance);

        walletRepo.save(wallet);
        walletRepo.appendLedger(ledger);
        publisher.publish(new WalletBalanceChanged(cmd.accountId(), cmd.currency(), cmd.amount(), balance, reason));

        return new WalletView(wallet.balanceOf(Currency.CASH), wallet.balanceOf(Currency.GEMS));
    }
}
