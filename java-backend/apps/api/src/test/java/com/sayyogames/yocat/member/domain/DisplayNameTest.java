package com.sayyogames.yocat.member.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the DisplayName value object (EDD §4.5.2 class-domain, Account Invariant Table:
 * 長度 ∈ [1,24]; CONSTANTS §8 DISPLAY_NAME_MIN=1, DISPLAY_NAME_MAX=24). Pure domain.
 *
 * <p>The JUnit {@code @DisplayName} annotation is referenced fully-qualified throughout because the
 * domain value object under test shares its simple name within this package.
 *
 * <p>RED phase: DisplayName length validation is realised by domain-entities (O-01); these tests
 * assert the target behaviour and therefore FAIL until that GREEN step lands.
 *
 * <p>Traceability: TC-UNIT-MEM-003-B01 (RTM §3.2 — 長度不合法的註冊欄位被拒絕，回 400);
 * PRD §5.1 AC-3.
 */
class DisplayNameTest {

    @Test
    @org.junit.jupiter.api.DisplayName("下限邊界長度 1 被接受")
    void of_atLowerBound_isAccepted() {
        assertEquals("喵", DisplayName.of("喵").value());
    }

    @Test
    @org.junit.jupiter.api.DisplayName("上限邊界長度 24 被接受")
    void of_atUpperBound_isAccepted() {
        String name24 = "x".repeat(24);
        assertEquals(name24, DisplayName.of(name24).value());
    }

    @Test
    @org.junit.jupiter.api.DisplayName("TC-UNIT-MEM-003-B01: 長度 0（空字串）被拒絕（回 400）")
    void of_belowLowerBound_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> DisplayName.of(""));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("TC-UNIT-MEM-003-B01: 長度 25（> 24）被拒絕（回 400）")
    void of_aboveUpperBound_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> DisplayName.of("x".repeat(25)));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("null 名稱被拒絕")
    void of_withNull_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> DisplayName.of(null));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("value 語意相等契約：equals/hashCode 以 value 為依據")
    void equalsAndHashCode_valueSemantics() {
        DisplayName boss = DisplayName.of("喵星辦公室");

        assertEquals(boss, boss, "same reference is equal");
        assertEquals(boss, DisplayName.of("喵星辦公室"), "same value is equal");
        assertEquals(DisplayName.of("喵星辦公室").hashCode(), boss.hashCode(), "equal values share hashCode");
        org.junit.jupiter.api.Assertions.assertNotEquals(boss, DisplayName.of("另一間公司"), "different value is not equal");
        org.junit.jupiter.api.Assertions.assertNotEquals(boss, null, "never equal to null");
        org.junit.jupiter.api.Assertions.assertNotEquals(boss, "喵星辦公室", "never equal to a different type");
        assertEquals("喵星辦公室", boss.toString(), "toString is the raw value");
    }
}
