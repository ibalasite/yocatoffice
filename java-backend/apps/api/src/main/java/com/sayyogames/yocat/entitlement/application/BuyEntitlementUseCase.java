package com.sayyogames.yocat.entitlement.application;

import com.sayyogames.yocat.entitlement.domain.Entitlement;
import com.sayyogames.yocat.entitlement.domain.EntitlementRepository;
import com.sayyogames.yocat.wallet.api.WalletService;
import com.sayyogames.yocat.wallet.application.WalletView;
import java.util.List;
import java.util.Locale;

/**
 * &lt;&lt;UseCase&gt;&gt; atomic cross-module buy: debit wallet then grant entitlement (EDD §4.5.4.3; {@code POST /entitlements/buy}).
 *
 * <p>Orchestration (EDD §4.5.4.3 / §4.5.4.4): reject a blank {@code itemId} (→ 400); reject a duplicate
 * ownership of {@code (kind, itemId)} (→ 409) <em>before</em> any debit; debit the wallet through its
 * Public API {@link WalletService} in the same transaction (propagating 402 on insufficient balance);
 * then grant (persist) the entitlement and return the post-debit wallet plus the owned entitlement.
 * Consumes the wallet BC only through its injected Public interface (EDD §4.3 module DAG; DIP).</p>
 */
public class BuyEntitlementUseCase {

    private final EntitlementRepository entRepo;
    private final WalletService walletService;

    public BuyEntitlementUseCase(EntitlementRepository entRepo, WalletService walletService) {
        this.entRepo = entRepo;
        this.walletService = walletService;
    }

    /**
     * Buy and grant one entitlement, debiting the wallet atomically.
     * @throws IllegalArgumentException missing/blank itemId → 400 (no debit, no grant)
     * @throws AlreadyOwnedException account already owns (kind, itemId) → 409 (no debit, no grant)
     * @throws com.sayyogames.yocat.wallet.domain.InsufficientBalanceException balance &lt; amount → 402 (no grant)
     */
    public BuyResult execute(BuyCommand cmd) {
        if (cmd.itemId() == null || cmd.itemId().isBlank()) {
            throw new IllegalArgumentException("itemId 不可為空");
        }
        if (entRepo.existsOwned(cmd.accountId(), cmd.kind(), cmd.itemId())) {
            throw new AlreadyOwnedException();
        }

        String reason = "buy_" + cmd.kind().name().toLowerCase(Locale.ROOT); // buy_cosmetic / buy_dream / buy_scenario
        WalletView wallet = walletService.debit(cmd.accountId(), cmd.currency(), cmd.amount(), reason);

        Entitlement granted = entRepo.save(Entitlement.grant(cmd.accountId(), cmd.kind(), cmd.itemId()));
        return new BuyResult(wallet, List.of(granted));
    }
}
