package com.sayyogames.yocat.persistence.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sayyogames.yocat.persistence.support.AbstractSchemaIT;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * DB-contract integration tests for the {@code wallet} BC schema (SCHEMA §3.2): the non-negative
 * balance CHECK that forbids overdraft, the append-only ledger CHECK constraints, and the
 * {@code SELECT ... FOR UPDATE} row-lock that serialises concurrent debits so the wallet is never
 * overdrawn and reconciles with the ledger (test-plan §3.2 行鎖一致性 / §3.5 CONS-01, CONS-02;
 * PRD §9.1 對帳 0 筆不一致 north-star guard).
 */
class WalletSchemaIT extends AbstractSchemaIT {

    @Test
    @DisplayName("wallets.cash CHECK (cash >= 0) forbids a negative balance (overdraft)")
    void cashCheckForbidsNegativeBalance() {
        long acct = insertAccount("wal1@example.com", "Wallet One");

        assertThatThrownBy(() ->
                jdbc.update("INSERT INTO wallet.wallets(account_id, cash, gems) VALUES (?, ?, 0)", acct, -1))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("wallet_ledger.balance CHECK (balance >= 0) forbids a negative post-change balance")
    void ledgerBalanceCheckForbidsNegativeBalance() {
        long acct = insertAccount("wal2@example.com", "Wallet Two");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO wallet.wallet_ledger(account_id, currency, delta, reason, balance) "
                        + "VALUES (?, 'cash', -100, 'buy_cosmetic', -100)", acct))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("wallet_ledger.reason CHECK accepts init/topup/rescue and buy_* but rejects unknown reasons")
    void ledgerReasonCheckConstrainsReasonSet() {
        long acct = insertAccount("wal3@example.com", "Wallet Three");

        jdbc.update("INSERT INTO wallet.wallet_ledger(account_id, currency, delta, reason, balance) "
                + "VALUES (?, 'cash', 500, 'topup', 500)", acct);
        jdbc.update("INSERT INTO wallet.wallet_ledger(account_id, currency, delta, reason, balance) "
                + "VALUES (?, 'cash', -200, 'buy_scenario', 300)", acct);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO wallet.wallet_ledger(account_id, currency, delta, reason, balance) "
                        + "VALUES (?, 'cash', -1, 'frobnicate', 299)", acct))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("CONS-01: SELECT FOR UPDATE serialises 10 concurrent debits so the final balance reconciles with the ledger")
    void selectForUpdateSerialisesConcurrentDebitsToZero() throws Exception {
        long acct = insertAccount("wal4@example.com", "Wallet Four");
        jdbc.update("INSERT INTO wallet.wallets(account_id, cash, gems) VALUES (?, 10000, 0)", acct);

        int successes = runConcurrentDebits(acct, 10, 1000);

        assertThat(successes).isEqualTo(10);
        Long finalCash = jdbc.queryForObject(
                "SELECT cash FROM wallet.wallets WHERE account_id = ?", Long.class, acct);
        Long ledgerCount = jdbc.queryForObject(
                "SELECT count(*) FROM wallet.wallet_ledger WHERE account_id = ?", Long.class, acct);
        Long ledgerSum = jdbc.queryForObject(
                "SELECT coalesce(sum(delta), 0) FROM wallet.wallet_ledger WHERE account_id = ?", Long.class, acct);
        assertThat(finalCash).isZero();
        assertThat(ledgerCount).isEqualTo(10);
        assertThat(10000L + ledgerSum).isEqualTo(finalCash);
    }

    @Test
    @DisplayName("CONS-02: concurrent debits past the balance boundary never overdraw; exactly the affordable count succeeds")
    void concurrentDebitsNeverOverdrawAtBoundary() throws Exception {
        long acct = insertAccount("wal5@example.com", "Wallet Five");
        jdbc.update("INSERT INTO wallet.wallets(account_id, cash, gems) VALUES (?, 5000, 0)", acct);

        int successes = runConcurrentDebits(acct, 10, 1000);

        assertThat(successes).isEqualTo(5);
        Long finalCash = jdbc.queryForObject(
                "SELECT cash FROM wallet.wallets WHERE account_id = ?", Long.class, acct);
        assertThat(finalCash).isZero();
        assertThat(finalCash).isGreaterThanOrEqualTo(0L);
    }

    /**
     * Runs {@code taskCount} concurrent debit attempts of {@code amount} against one wallet row, each in
     * its own transaction using {@code SELECT ... FOR UPDATE} to take the row lock. Returns the number of
     * debits that succeeded (balance stayed {@code >= 0}).
     */
    private int runConcurrentDebits(long acct, int taskCount, long amount) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(taskCount);
        CyclicBarrier barrier = new CyclicBarrier(taskCount);
        try {
            List<Callable<Boolean>> tasks = new ArrayList<>();
            for (int i = 0; i < taskCount; i++) {
                tasks.add(() -> {
                    barrier.await(10, TimeUnit.SECONDS);
                    return debitWithRowLock(acct, amount);
                });
            }
            List<Future<Boolean>> futures = pool.invokeAll(tasks);
            int successes = 0;
            for (Future<Boolean> f : futures) {
                if (Boolean.TRUE.equals(f.get(30, TimeUnit.SECONDS))) {
                    successes++;
                }
            }
            return successes;
        } finally {
            pool.shutdownNow();
        }
    }

    private boolean debitWithRowLock(long acct, long amount) throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                long balance;
                try (PreparedStatement sel = conn.prepareStatement(
                        "SELECT cash FROM wallet.wallets WHERE account_id = ? FOR UPDATE")) {
                    sel.setLong(1, acct);
                    try (ResultSet rs = sel.executeQuery()) {
                        rs.next();
                        balance = rs.getLong(1);
                    }
                }
                if (balance < amount) {
                    conn.rollback();
                    return false;
                }
                long newBalance = balance - amount;
                try (PreparedStatement upd = conn.prepareStatement(
                        "UPDATE wallet.wallets SET cash = ? WHERE account_id = ?")) {
                    upd.setLong(1, newBalance);
                    upd.setLong(2, acct);
                    upd.executeUpdate();
                }
                try (PreparedStatement led = conn.prepareStatement(
                        "INSERT INTO wallet.wallet_ledger(account_id, currency, delta, reason, balance) "
                                + "VALUES (?, 'cash', ?, 'buy_cosmetic', ?)")) {
                    led.setLong(1, acct);
                    led.setLong(2, -amount);
                    led.setLong(3, newBalance);
                    led.executeUpdate();
                }
                conn.commit();
                return true;
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }
    }
}
