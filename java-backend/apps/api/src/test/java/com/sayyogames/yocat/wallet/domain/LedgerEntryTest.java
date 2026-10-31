package com.sayyogames.yocat.wallet.domain;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sayyogames.yocat.shared.AccountId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the LedgerEntry entity (EDD §4.5.2 class-domain, Invariant Table).
 *
 * <p>A ledger line is append-only and produced only by the Wallet aggregate; these tests verify the
 * Σdelta = balance reconciliation invariant through the entries emitted by {@link Wallet#debit} /
 * {@link Wallet#credit}. Pure domain — no Spring, no JDBC.
 *
 * <p>RED phase: the entry-producing behaviour is realised by domain-entities (O-01); these tests
 * assert the target behaviour and therefore FAIL until that GREEN step lands.
 *
 * <p>Traceability: TC-UNIT-LEDGERENTRY (class-inventory.md); EDD §4.5.3 Object Diagram (ledger_spend_42).
 */
class LedgerEntryTest {

    private static final AccountId ALICE = AccountId.of(1001L);

    @Test
    @DisplayName("扣款產生的 ledger 其 delta 為負且 balance 等於異動後餘額")
    void debitEntry_hasNegativeDeltaAndPostMutationBalance() {
        Wallet wallet = Wallet.of(ALICE, 20_000L, 0L);

        LedgerEntry entry = wallet.debit(Currency.CASH, 1_250L);

        assertAll(
            () -> assertTrue(entry.delta() < 0L, "a spend records a negative delta"),
            () -> assertEquals(-1_250L, entry.delta()),
            () -> assertEquals(18_750L, entry.balance(), "balance = opening (20000) + delta (-1250)"),
            () -> assertEquals(entry.balance(), 20_000L + entry.delta(), "Σdelta = balance reconciliation invariant"),
            () -> assertEquals(ALICE, entry.accountId()),
            () -> assertEquals(Currency.CASH, entry.currency())
        );
    }

    @Test
    @DisplayName("充值產生的 ledger 其 delta 為正且 reason 標記為 TOPUP")
    void creditEntry_hasPositiveDeltaAndTopUpReason() {
        Wallet wallet = Wallet.of(ALICE, 0L, 0L);

        LedgerEntry entry = wallet.credit(Currency.GEMS, 330L);

        assertAll(
            () -> assertTrue(entry.delta() > 0L, "a top-up records a positive delta"),
            () -> assertEquals(330L, entry.balance(), "balance = opening (0) + delta (330)"),
            () -> assertEquals(LedgerReason.TOPUP, entry.reason()),
            () -> assertEquals(Currency.GEMS, entry.currency())
        );
    }
}
