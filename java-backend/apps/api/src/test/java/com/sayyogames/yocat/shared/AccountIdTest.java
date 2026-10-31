package com.sayyogames.yocat.shared;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the AccountId value object (EDD §4.5.2 class-domain, SharedKernel).
 * SCHEMA member.accounts.id is BIGSERIAL (&gt; 0). Pure domain — no Spring, no JDBC.
 *
 * <p>RED phase: AccountId value-equality semantics and its positivity invariant are realised by
 * domain-entities (O-01); these tests assert the target behaviour and therefore FAIL until that
 * GREEN step lands.
 *
 * <p>Traceability: TC-UNIT-ACCOUNTID (class-inventory.md).
 */
class AccountIdTest {

    @Test
    @DisplayName("相同原始值的兩個 AccountId 以值相等（Value Object 語意）")
    void equals_sameRawValue_areEqual() {
        AccountId a = AccountId.of(1001L);
        AccountId b = AccountId.of(1001L);

        assertAll(
            () -> assertEquals(a, b, "value objects with the same raw value are equal"),
            () -> assertEquals(a.hashCode(), b.hashCode(), "equal value objects share a hash code"),
            () -> assertEquals(1001L, a.value())
        );
    }

    @Test
    @DisplayName("不同原始值的 AccountId 不相等")
    void equals_differentRawValue_areNotEqual() {
        assertNotEquals(AccountId.of(1001L), AccountId.of(2002L));
    }

    @Test
    @DisplayName("帳號 ID 必須為正（BIGSERIAL > 0）")
    void of_withNonPositiveRaw_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> AccountId.of(0L));
        assertThrows(IllegalArgumentException.class, () -> AccountId.of(-1L));
    }
}
