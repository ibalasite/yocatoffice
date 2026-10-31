package com.sayyogames.yocat.entitlement.application;

import com.sayyogames.yocat.entitlement.domain.Entitlement;
import com.sayyogames.yocat.entitlement.domain.EntitlementGranted;
import com.sayyogames.yocat.entitlement.domain.EntitlementKind;
import com.sayyogames.yocat.entitlement.domain.EntitlementRepository;
import com.sayyogames.yocat.shared.AccountId;
import com.sayyogames.yocat.shared.DomainEventPublisher;
import com.sayyogames.yocat.wallet.application.WalletView;
import com.sayyogames.yocat.wallet.api.WalletService;
import com.sayyogames.yocat.wallet.domain.Currency;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * &lt;&lt;ApplicationService&gt;&gt; entitlement BC atomic purchase (EDD §4.5.4.3 / API §3 entitlement
 * {@code POST /entitlements/buy}). Wraps {@link BuyEntitlementUseCase} in a transaction so the wallet debit,
 * the grant and the {@code EntitlementGranted} Outbox publication commit atomically, then publishes the event.
 *
 * <p>A zero-amount purchase is a free grant (PRD US-ENT-001; CONSTANTS §7 price 0): it skips the wallet debit
 * (no ledger line) and only inserts the entitlement — still guarded by the UNIQUE(account,kind,item) idempotency
 * rule (409) and still publishing {@code EntitlementGranted}.</p>
 */
@Service
public class EntitlementAppService {

    private final BuyEntitlementUseCase buyUseCase;
    private final EntitlementRepository entRepo;
    private final WalletService walletService;
    private final DomainEventPublisher publisher;

    public EntitlementAppService(BuyEntitlementUseCase buyUseCase, EntitlementRepository entRepo,
                                 WalletService walletService, DomainEventPublisher publisher) {
        this.buyUseCase = buyUseCase;
        this.entRepo = entRepo;
        this.walletService = walletService;
        this.publisher = publisher;
    }

    @Transactional
    public BuyResult buy(long accountId, EntitlementKind kind, String itemId, Currency currency, long amount) {
        AccountId acct = AccountId.of(accountId);
        BuyResult result;
        if (amount <= 0L) {
            result = freeGrant(acct, kind, itemId);
        } else {
            result = buyUseCase.execute(new BuyCommand(acct, kind, itemId, currency, amount));
        }
        publisher.publish(new EntitlementGranted(acct, kind, itemId));
        return result;
    }

    private BuyResult freeGrant(AccountId acct, EntitlementKind kind, String itemId) {
        if (itemId == null || itemId.isBlank()) {
            throw new IllegalArgumentException("項目不正確");
        }
        if (entRepo.existsOwned(acct, kind, itemId)) {
            throw new AlreadyOwnedException();
        }
        Entitlement granted = entRepo.save(Entitlement.grant(acct, kind, itemId));
        WalletView wallet = walletService.findWallet(acct); // unchanged — a free grant writes no ledger line
        return new BuyResult(wallet, List.of(granted));
    }
}
