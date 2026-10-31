package com.sayyogames.yocat.wallet.application;

import com.sayyogames.yocat.shared.AccountId;
import com.sayyogames.yocat.wallet.api.WalletService;
import com.sayyogames.yocat.wallet.domain.Currency;
import com.sayyogames.yocat.wallet.domain.LedgerReason;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * &lt;&lt;ApplicationService&gt;&gt; transactional wallet orchestration for the presentation layer (API §3 wallet:
 * {@code POST /wallet/topup|spend}). Wraps the use cases in a transaction so the balance mutation, the
 * append-only ledger line and the {@code WalletBalanceChanged} Outbox publication all commit atomically
 * (EDD §4.5.4.1 / §4.6).
 *
 * <p>A client {@code reason} on spend is free-form (API §2.3); it is canonicalised here to the
 * CONSTANTS §9 ledger-reason set the {@code wallet.wallet_ledger} CHECK accepts — a recognised reason name is
 * kept, anything else is recorded as {@code rescue} (the generic non-purchase outflow). Top-up always records
 * {@code topup}.</p>
 */
@Service
public class WalletAppService {

    private final TopUpWalletUseCase topUpUseCase;
    private final SpendWalletUseCase spendUseCase;
    private final WalletService walletService;

    public WalletAppService(TopUpWalletUseCase topUpUseCase, SpendWalletUseCase spendUseCase,
                            WalletService walletService) {
        this.topUpUseCase = topUpUseCase;
        this.spendUseCase = spendUseCase;
        this.walletService = walletService;
    }

    @Transactional
    public WalletView topUp(AccountId accountId, Currency currency, long amount) {
        return topUpUseCase.execute(new TopUpCommand(accountId, currency, amount, "topup"));
    }

    @Transactional
    public WalletView spend(AccountId accountId, Currency currency, long amount, String clientReason) {
        return spendUseCase.execute(new SpendCommand(accountId, currency, amount, canonicalReason(clientReason)));
    }

    @Transactional(readOnly = true)
    public WalletView find(AccountId accountId) {
        return walletService.findWallet(accountId);
    }

    /** Map a free-form client reason to a CHECK-valid CONSTANTS §9 ledger reason (default {@code rescue}). */
    private static String canonicalReason(String clientReason) {
        if (clientReason == null || clientReason.isBlank()) {
            return "rescue";
        }
        try {
            return LedgerReason.valueOf(clientReason.toUpperCase(Locale.ROOT)).name().toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException notAnEnumReason) {
            return "rescue";
        }
    }
}
