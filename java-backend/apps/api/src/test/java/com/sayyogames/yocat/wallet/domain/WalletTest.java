package com.sayyogames.yocat.wallet.domain;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sayyogames.yocat.shared.AccountId;
import com.sayyogames.yocat.shared.InvalidAmountException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the Wallet aggregate root (EDD §4.5.2 class-domain, §4.5.6 state-machine-wallet,
 * §4.7 Algorithm 1). Pure domain — no Spring, no JDBC, no mock framework.
 *
 * <p>RED phase: Wallet.debit/credit/balanceOf are realised by domain-entities (O-01); these tests
 * assert the target behaviour and therefore FAIL until that GREEN step lands.
 *
 * <p>Traceability: TC-UNIT-WALLET-S01, TC-UNIT-WALLET-E01, TC-UNIT-WAL-004-B01 (RTM §3.2);
 * PRD §5.2 AC-3 (餘額不足 → 402), AC-4 (金額不正確 → 400).
 */
class WalletTest {

    private static final AccountId ALICE = AccountId.of(1001L);

    @Nested
    @DisplayName("Wallet.debit — spend")
    class Debit {

        @Test
        @DisplayName("TC-UNIT-WALLET-S01: 餘額足夠時扣款成功並產生負 delta 異動")
        void debit_withSufficientBalance_appendsNegativeDeltaLedgerAndLowersBalance() {
            Wallet wallet = Wallet.of(ALICE, 20_000L, 310L);

            LedgerEntry entry = wallet.debit(Currency.CASH, 1_250L);

            assertAll(
                () -> assertEquals(-1_250L, entry.delta(), "ledger delta must be the negated spend amount"),
                () -> assertEquals(18_750L, entry.balance(), "ledger records post-mutation balance (20000-1250)"),
                () -> assertEquals(Currency.CASH, entry.currency()),
                () -> assertEquals(18_750L, wallet.balanceOf(Currency.CASH), "cash balance decreases by amount"),
                () -> assertEquals(310L, wallet.balanceOf(Currency.GEMS), "other currency is untouched")
            );
        }

        @Test
        @DisplayName("TC-UNIT-WALLET-E01: 餘額不足時拒絕扣款（不產生異動、餘額不變）")
        void debit_withInsufficientBalance_throwsAndLeavesBalanceUnchanged() {
            Wallet wallet = Wallet.of(ALICE, 800L, 0L);

            InsufficientBalanceException ex =
                assertThrows(InsufficientBalanceException.class, () -> wallet.debit(Currency.CASH, 1_250L));

            assertAll(
                () -> assertEquals(800L, ex.balance(), "402 carries the unchanged balance"),
                () -> assertEquals(800L, wallet.balanceOf(Currency.CASH), "balance must not change on a rejected debit")
            );
        }

        @Test
        @DisplayName("TC-UNIT-WAL-004-B01: 金額 <= 0 的扣款被拒絕（回 400）")
        void debit_withNonPositiveAmount_throwsInvalidAmount() {
            Wallet wallet = Wallet.of(ALICE, 20_000L, 0L);

            assertThrows(InvalidAmountException.class, () -> wallet.debit(Currency.CASH, 0L));
            assertThrows(InvalidAmountException.class, () -> wallet.debit(Currency.CASH, -5L));
        }

        @Test
        @DisplayName("金額超過上限 10,000,000 的扣款被拒絕（回 400）")
        void debit_aboveUpperBound_throwsInvalidAmount() {
            Wallet wallet = Wallet.of(ALICE, 20_000L, 0L);

            assertThrows(InvalidAmountException.class, () -> wallet.debit(Currency.CASH, 10_000_001L));
        }

        @Test
        @DisplayName("扣到剛好歸零（balance == amount）成功且餘額恆 >= 0")
        void debit_exactBalance_reachesZeroAndStaysNonNegative() {
            Wallet wallet = Wallet.of(ALICE, 1_250L, 0L);

            LedgerEntry entry = wallet.debit(Currency.CASH, 1_250L);

            assertEquals(0L, entry.balance());
            assertEquals(0L, wallet.balanceOf(Currency.CASH));
            assertTrue(wallet.balanceOf(Currency.CASH) >= 0L, "underflow guard: balance never negative");
        }
    }

    @Nested
    @DisplayName("Wallet.credit — top-up")
    class Credit {

        @Test
        @DisplayName("充值產生正 delta 異動並提高餘額")
        void credit_appendsPositiveDeltaLedgerAndRaisesBalance() {
            Wallet wallet = Wallet.of(ALICE, 0L, 0L);

            LedgerEntry entry = wallet.credit(Currency.GEMS, 330L);

            assertAll(
                () -> assertEquals(330L, entry.delta(), "credit delta is positive"),
                () -> assertEquals(330L, entry.balance()),
                () -> assertEquals(Currency.GEMS, entry.currency()),
                () -> assertEquals(330L, wallet.balanceOf(Currency.GEMS))
            );
        }
    }
}
