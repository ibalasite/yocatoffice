package com.sayyogames.yocat.wallet.infrastructure;

import com.sayyogames.yocat.shared.AccountId;
import com.sayyogames.yocat.wallet.domain.Currency;
import com.sayyogames.yocat.wallet.domain.LedgerEntry;
import com.sayyogames.yocat.wallet.domain.Wallet;
import com.sayyogames.yocat.wallet.domain.WalletRepository;
import java.util.Locale;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Infrastructure JDBC adapter for the {@link WalletRepository} port (O-04, EDD §4.5.4.1 / §4.7 Algorithm 1;
 * §2 DIP). Implements the domain-owned port against {@code wallet.wallets} and the append-only
 * {@code wallet.wallet_ledger} on the real PostgreSQL 16 schema (SCHEMA §3.2).
 *
 * <p>{@link #findForUpdate} issues {@code SELECT ... FOR UPDATE} to take a pessimistic row lock that
 * serialises concurrent debit/credit under the caller's transaction (test-plan §3.5 CONS-01): because the
 * injected {@link JdbcTemplate} runs on the transaction-bound connection, the lock is held until the caller
 * commits, so interleaved debits can never overdraw. The CHECK {@code cash >= 0 / gems >= 0} is the final DB
 * backstop. All statements are parameterised.</p>
 */
@Repository
public class JdbcWalletRepository implements WalletRepository {

    private final JdbcTemplate jdbc;

    public JdbcWalletRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static RowMapper<Wallet> walletMapper(AccountId id) {
        return (rs, rowNum) -> Wallet.of(id, rs.getLong("cash"), rs.getLong("gems"));
    }

    @Override
    public Optional<Wallet> findForUpdate(AccountId id, Currency currency) {
        return jdbc.query(
                "SELECT account_id, cash, gems FROM wallet.wallets WHERE account_id = ? FOR UPDATE",
                walletMapper(id), id.value()).stream().findFirst();
    }

    @Override
    public Optional<Wallet> findByAccount(AccountId id) {
        return jdbc.query(
                "SELECT account_id, cash, gems FROM wallet.wallets WHERE account_id = ?",
                walletMapper(id), id.value()).stream().findFirst();
    }

    @Override
    public Wallet save(Wallet wallet) {
        long accountId = wallet.accountId().value();
        jdbc.update(
                "INSERT INTO wallet.wallets(account_id, cash, gems) VALUES (?, ?, ?) "
                        + "ON CONFLICT (account_id) DO UPDATE SET cash = EXCLUDED.cash, gems = EXCLUDED.gems",
                accountId, wallet.balanceOf(Currency.CASH), wallet.balanceOf(Currency.GEMS));
        return wallet;
    }

    @Override
    public void appendLedger(LedgerEntry entry) {
        jdbc.update(
                "INSERT INTO wallet.wallet_ledger(account_id, currency, delta, reason, balance) "
                        + "VALUES (?, ?, ?, ?, ?)",
                entry.accountId().value(),
                entry.currency().name().toLowerCase(Locale.ROOT),
                entry.delta(),
                entry.reason().name().toLowerCase(Locale.ROOT),
                entry.balance());
    }
}
