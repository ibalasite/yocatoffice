package com.sayyogames.yocat.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.sayyogames.yocat.persistence.support.AbstractRepositoryIT;
import com.sayyogames.yocat.shared.AccountId;
import com.sayyogames.yocat.wallet.domain.Currency;
import com.sayyogames.yocat.wallet.domain.LedgerEntry;
import com.sayyogames.yocat.wallet.domain.Wallet;
import com.sayyogames.yocat.wallet.domain.WalletRepository;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * RED integration test for the {@code JdbcWalletRepository} adapter behind {@link WalletRepository}
 * (O-04, EDD §4.5.4.1 / §4.7 Algorithm 1). Covers every port method against the real PostgreSQL 16
 * container — {@code findForUpdate} (pessimistic {@code SELECT ... FOR UPDATE}), {@code findByAccount},
 * {@code save}, {@code appendLedger} — plus the headline concurrent-debit row-lock vector through the
 * adapter (test-plan §3.1 "JdbcWalletRepositoryIT ... 驗證 SELECT ... FOR UPDATE 行鎖"; §3.5 CONS-01).
 * The adapter bean does not exist until {@code persist-repo-impls}, so the suite is RED (Review #11).
 */
class JdbcWalletRepositoryIT extends AbstractRepositoryIT {

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private long seedWallet(String email, long cash, long gems) {
        long acct = seedAccount(email, "Wallet Owner");
        jdbc.update("INSERT INTO wallet.wallets(account_id, cash, gems) VALUES (?, ?, ?)", acct, cash, gems);
        return acct;
    }

    @Test
    @DisplayName("findByAccount returns the persisted wallet balances on the no-lock read path")
    void findByAccountReturnsBalances() {
        AccountId acct = AccountId.of(seedWallet("w1@example.com", 1500, 42));

        Optional<Wallet> wallet = walletRepository.findByAccount(acct);

        assertThat(wallet).isPresent();
        assertThat(wallet.get().balanceOf(Currency.CASH)).isEqualTo(1500);
        assertThat(wallet.get().balanceOf(Currency.GEMS)).isEqualTo(42);
    }

    @Test
    @DisplayName("findForUpdate loads the wallet row within a transaction and is empty when absent")
    void findForUpdateLoadsRowAndIsEmptyWhenAbsent() {
        AccountId acct = AccountId.of(seedWallet("w2@example.com", 800, 0));
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        Optional<Wallet> locked = tx.execute(status -> walletRepository.findForUpdate(acct, Currency.CASH));
        assertThat(locked).isPresent();
        assertThat(locked.get().balanceOf(Currency.CASH)).isEqualTo(800);

        Optional<Wallet> missing = tx.execute(status ->
                walletRepository.findForUpdate(AccountId.of(9_999_999L), Currency.CASH));
        assertThat(missing).isEmpty();
    }

    @Test
    @DisplayName("save persists the mutated balance after a domain debit")
    void savePersistsMutatedBalance() {
        AccountId acct = AccountId.of(seedWallet("w3@example.com", 1000, 0));
        Wallet wallet = Wallet.of(acct, 1000, 0);
        wallet.debit(Currency.CASH, 400);

        walletRepository.save(wallet);

        Long cash = jdbc.queryForObject(
                "SELECT cash FROM wallet.wallets WHERE account_id = ?", Long.class, acct.value());
        assertThat(cash).isEqualTo(600);
    }

    @Test
    @DisplayName("appendLedger writes an immutable ledger line with the post-change balance")
    void appendLedgerWritesAuditLine() {
        AccountId acct = AccountId.of(seedWallet("w4@example.com", 1000, 0));
        LedgerEntry entry = LedgerEntry.of(acct, Currency.CASH, -250,
                com.sayyogames.yocat.wallet.domain.LedgerReason.BUY_COSMETIC, 750);

        walletRepository.appendLedger(entry);

        Long ledgerRows = jdbc.queryForObject(
                "SELECT count(*) FROM wallet.wallet_ledger WHERE account_id = ? AND delta = -250 AND balance = 750",
                Long.class, acct.value());
        assertThat(ledgerRows).isEqualTo(1);
    }

    @Test
    @DisplayName("CONS-01 via adapter: concurrent findForUpdate+save debits serialise and never overdraw")
    void concurrentDebitsThroughAdapterSerialise() throws Exception {
        AccountId acct = AccountId.of(seedWallet("w5@example.com", 10000, 0));
        int taskCount = 10;
        long amount = 1000;
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        ExecutorService pool = Executors.newFixedThreadPool(taskCount);
        CyclicBarrier barrier = new CyclicBarrier(taskCount);
        try {
            List<Callable<Boolean>> tasks = new java.util.ArrayList<>();
            for (int i = 0; i < taskCount; i++) {
                tasks.add((Callable<Boolean>) () -> {
                    barrier.await(10, TimeUnit.SECONDS);
                    return Boolean.TRUE.equals(tx.execute(status -> {
                        Wallet wallet = walletRepository.findForUpdate(acct, Currency.CASH).orElseThrow();
                        if (wallet.balanceOf(Currency.CASH) < amount) {
                            return false;
                        }
                        LedgerEntry entry = wallet.debit(Currency.CASH, amount);
                        walletRepository.save(wallet);
                        walletRepository.appendLedger(entry);
                        return true;
                    }));
                });
            }
            List<Future<Boolean>> futures = pool.invokeAll(tasks);
            int successes = 0;
            for (Future<Boolean> f : futures) {
                if (Boolean.TRUE.equals(f.get(30, TimeUnit.SECONDS))) {
                    successes++;
                }
            }
            assertThat(successes).isEqualTo(10);
        } finally {
            pool.shutdownNow();
        }

        Long cash = jdbc.queryForObject(
                "SELECT cash FROM wallet.wallets WHERE account_id = ?", Long.class, acct.value());
        assertThat(cash).isZero();
    }
}
