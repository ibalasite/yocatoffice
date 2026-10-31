package com.sayyogames.yocat.member.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for the Email value object (EDD §4.5.2 class-domain, Account Invariant Table:
 * 格式合法、全域唯一). Pure domain — no Spring, no JDBC.
 *
 * <p>RED phase: Email format validation is realised by domain-entities (O-01); these tests assert
 * the target behaviour and therefore FAIL until that GREEN step lands.
 *
 * <p>Traceability: TC-UNIT-MEM-003-B01 (RTM §3.2 — 格式不合法的註冊欄位被拒絕，回 400);
 * PRD §5.1 AC-3.
 */
class EmailTest {

    @Test
    @DisplayName("合法 email 建立成功並保留正規化後的值")
    void of_withValidEmail_keepsValue() {
        Email email = Email.of("alice@example.com");

        assertEquals("alice@example.com", email.value());
    }

    @Test
    @DisplayName("大小寫在建立時正規化為小寫（全域唯一比較用）")
    void of_normalisesToLowerCase() {
        assertEquals("alice@example.com", Email.of("Alice@Example.COM").value());
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-an-email", "alice@", "@example.com", "alice example.com", ""})
    @DisplayName("TC-UNIT-MEM-003-B01: 格式不合法的 email 被拒絕（回 400）")
    void of_withInvalidFormat_isRejected(String raw) {
        assertThrows(IllegalArgumentException.class, () -> Email.of(raw));
    }

    @Test
    @DisplayName("null email 被拒絕")
    void of_withNull_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> Email.of(null));
    }

    @Test
    @DisplayName("value 語意相等契約：equals/hashCode 以正規化後的 value 為依據（大小寫不敏感）")
    void equalsAndHashCode_valueSemantics() {
        Email alice = Email.of("alice@example.com");

        assertEquals(alice, alice, "same reference is equal");
        assertEquals(alice, Email.of("Alice@Example.com"), "case-insensitive normalisation makes these equal");
        assertEquals(Email.of("alice@example.com").hashCode(), alice.hashCode(), "equal values share hashCode");
        org.junit.jupiter.api.Assertions.assertNotEquals(alice, Email.of("bob@example.com"), "different address is not equal");
        org.junit.jupiter.api.Assertions.assertNotEquals(alice, null, "never equal to null");
        org.junit.jupiter.api.Assertions.assertNotEquals(alice, "alice@example.com", "never equal to a different type");
        assertEquals("alice@example.com", alice.toString(), "toString is the normalised value");
    }
}
