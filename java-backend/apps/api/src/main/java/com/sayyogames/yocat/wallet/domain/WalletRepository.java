package com.sayyogames.yocat.wallet.domain;

import com.sayyogames.yocat.shared.AccountId;
import java.util.Optional;

/**
 * &lt;&lt;Repository&gt;&gt; port for the {@link Wallet} aggregate (wallet BC).
 *
 * <p>Domain-owned interface; JdbcWalletRepository (infrastructure, {@code SELECT ... FOR UPDATE}) is
 * injected via Spring DI (DIP, EDD §2). Authoritative signatures: EDD §4.5.2 Class Diagram (class-domain).</p>
 */
public interface WalletRepository {

    /**
     * Pessimistically load the wallet for the given currency ({@code SELECT ... FOR UPDATE}) to serialise
     * concurrent debit/credit; empty when absent. Caller: SpendWalletUseCase / TopUpWalletUseCase (EDD §4.5.4.1).
     */
    Optional<Wallet> findForUpdate(AccountId id, Currency currency);

    /** Load the wallet by account on the read path (no lock); empty when absent. Caller: WalletService.findWallet / GetMeUseCase (EDD §4.1). */
    Optional<Wallet> findByAccount(AccountId id);

    /** Persist the mutated wallet balances. Caller: SpendWalletUseCase / TopUpWalletUseCase (EDD §4.5.4.1). */
    Wallet save(Wallet wallet);

    /**
     * Append one immutable audit line to {@code wallet.wallet_ledger} (balance = post-change balance;
     * Σdelta = balance invariant). Caller: SpendWalletUseCase / TopUpWalletUseCase (EDD §4.5.4.1).
     */
    void appendLedger(LedgerEntry entry);
}
