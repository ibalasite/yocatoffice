package com.sayyogames.yocat.wallet.infrastructure;

import com.sayyogames.yocat.deposit.domain.DepositConfirmed;
import com.sayyogames.yocat.shared.MemberRegistered;
import com.sayyogames.yocat.wallet.api.WalletService;
import com.sayyogames.yocat.wallet.domain.Currency;
import com.sayyogames.yocat.wallet.domain.LedgerEntry;
import com.sayyogames.yocat.wallet.domain.LedgerReason;
import com.sayyogames.yocat.wallet.domain.Wallet;
import com.sayyogames.yocat.wallet.domain.WalletRepository;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * wallet BC inbound domain-event consumers (EDD §3.4 / §4.6.1 — cross-module reaction via the transactional
 * Outbox, never a direct HTTP/call into the producing module; EDD §7.1 register → open wallet).
 *
 * <ul>
 *   <li>{@code MemberRegistered} → open the account's wallet at zero balance and write the opening
 *       ({@code init}) ledger line (idempotent: skipped if the wallet already exists).</li>
 *   <li>{@code DepositConfirmed} → credit the paid currency through the wallet Public API, writing a
 *       {@code topup} ledger line. Checkout is idempotent per {@code Idempotency-Key}, so one confirmed
 *       order publishes exactly one event ⇒ the wallet is credited exactly once (PRD US-WAL-001 AC-1).</li>
 * </ul>
 *
 * <p>{@link ApplicationModuleListener} delivers these after the producing transaction commits, via the
 * event-publication registry; delivery is made synchronous/deterministic by the {@code taskExecutor}
 * (AsyncConfig) so the opened/credited wallet is observable the instant the producing API call returns.</p>
 */
@Component
public class WalletEventListeners {

    private final WalletRepository walletRepo;
    private final WalletService walletService;

    public WalletEventListeners(WalletRepository walletRepo, WalletService walletService) {
        this.walletRepo = walletRepo;
        this.walletService = walletService;
    }

    @ApplicationModuleListener
    public void onMemberRegistered(MemberRegistered event) {
        if (walletRepo.findByAccount(event.accountId()).isPresent()) {
            return; // already provisioned — idempotent re-delivery
        }
        walletRepo.save(Wallet.of(event.accountId(), 0L, 0L));
        walletRepo.appendLedger(LedgerEntry.of(event.accountId(), Currency.CASH, 0L, LedgerReason.INIT, 0L));
    }

    @ApplicationModuleListener
    public void onDepositConfirmed(DepositConfirmed event) {
        Currency currency = Currency.valueOf(event.currency().toUpperCase(java.util.Locale.ROOT));
        walletService.topUp(event.accountId(), currency, event.amount(), "topup");
    }
}
