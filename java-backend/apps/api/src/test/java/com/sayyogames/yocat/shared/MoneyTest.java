package com.sayyogames.yocat.shared;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the Money value object (EDD §4.5.2 class-domain; CONSTANTS §8
 * TOPUP_AMOUNT_MIN=1, TOPUP_AMOUNT_MAX=10_000_000). Pure domain — no Spring, no JDBC.
 *
 * <p>RED phase: Money arithmetic (plus/minus/isNegative) and its non-negativity invariant are
 * realised by domain-entities (O-01); these tests assert the target behaviour and therefore FAIL
 * until that GREEN step lands.
 *
 * <p>Traceability: TC-UNIT-MONEY-B01 (RTM §3.2 — 金額 0 與上限邊界運算正確且不產生負值).
 */
class MoneyTest {

    private static final long MAX = 10_000_000L;

    @Test
    @DisplayName("TC-UNIT-MONEY-B01: 下限 0 與運算後歸零不產生負值")
    void subtractToZero_isNotNegative() {
        Money balance = Money.of(1_250L);

        Money result = balance.minus(Money.of(1_250L));

        assertAll(
            () -> assertEquals(0L, result.amount(), "1250 - 1250 = 0"),
            () -> assertFalse(result.isNegative(), "a zero result is not negative")
        );
    }

    @Test
    @DisplayName("TC-UNIT-MONEY-B01: 上限邊界 10,000,000 的加法運算正確")
    void addAtUpperBound_isCorrect() {
        Money result = Money.of(MAX - 1L).plus(Money.of(1L));

        assertEquals(MAX, result.amount(), "(1e7 - 1) + 1 = 1e7");
    }

    @Test
    @DisplayName("減去較大金額時被標記為負（underflow 偵測）")
    void subtractLargerAmount_isNegative() {
        Money result = Money.of(800L).minus(Money.of(1_250L));

        assertTrue(result.isNegative(), "800 - 1250 < 0 must be flagged negative");
    }

    @Test
    @DisplayName("金額不可為負：Money.of(負數) 被拒絕")
    void of_withNegativeRaw_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> Money.of(-1L));
    }

    @Test
    @DisplayName("value 語意相等契約：equals/hashCode 以 amount 為依據（EDD §4.5.2 ValueObject 不可變性）")
    void equalsAndHashCode_valueSemantics() {
        Money five = Money.of(5L);

        assertAll(
            () -> assertTrue(five.equals(five), "same reference is equal"),
            () -> assertTrue(five.equals(Money.of(5L)), "same amount is equal"),
            () -> assertEquals(Money.of(5L).hashCode(), five.hashCode(), "equal values share hashCode"),
            () -> assertFalse(five.equals(Money.of(6L)), "different amount is not equal"),
            () -> assertFalse(five.equals(null), "never equal to null"),
            () -> assertFalse(five.equals("5"), "never equal to a different type")
        );
    }

    @Test
    @DisplayName("非負金額 isNegative 回 false；toString 揭示 amount")
    void nonNegative_isNegativeFalse_andToString() {
        Money zero = Money.of(0L);

        assertAll(
            () -> assertFalse(zero.isNegative(), "0 is not negative"),
            () -> assertTrue(zero.toString().contains("0"), "toString exposes the amount")
        );
    }
}
