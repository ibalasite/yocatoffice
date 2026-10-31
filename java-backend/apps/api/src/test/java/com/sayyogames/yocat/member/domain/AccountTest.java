package com.sayyogames.yocat.member.domain;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the Account aggregate root (EDD §4.5.2 class-domain, Invariant Table;
 * §4.7 Algorithm 3 scrypt verify). Pure domain — no Spring, no JDBC, no mock framework.
 *
 * <p>RED phase: Account.verifyPassword (constant-time comparison against the stored scrypt hash)
 * is realised by domain-entities (O-01); these tests assert the target behaviour and therefore
 * FAIL until that GREEN step lands.
 *
 * <p>Traceability: TC-UNIT-ACCOUNT (class-inventory.md); PRD §5.1 (register / login identity).
 */
class AccountTest {

    private Account registeredAlice() {
        return Account.register(
            Email.of("alice@example.com"),
            com.sayyogames.yocat.member.domain.DisplayName.of("喵星辦公室"),
            "0123456789abcdef0123456789abcdef$a8345969b9232c5e13548edccc20e307205a1ebbb01fe7f098fdbcaefd265d0f");
    }

    @Test
    @DisplayName("註冊建立帳號並保留 email 與 displayName（id 由儲存時指派，初始為 null）")
    void register_retainsEmailAndDisplayName() {
        Account account = registeredAlice();

        assertAll(
            () -> assertEquals("alice@example.com", account.email().value()),
            () -> assertEquals("喵星辦公室", account.displayName().value()),
            () -> assertEquals(null, account.id(), "a freshly registered account has no persisted id yet")
        );
    }

    @Test
    @DisplayName("verifyPassword：正確密碼回 true")
    void verifyPassword_withCorrectPassword_returnsTrue() {
        Account account = registeredAlice();

        assertTrue(account.verifyPassword("secret1"),
            "the raw password used at registration must verify against the stored hash");
    }

    @Test
    @DisplayName("verifyPassword：錯誤密碼回 false（常數時間比對）")
    void verifyPassword_withWrongPassword_returnsFalse() {
        Account account = registeredAlice();

        assertFalse(account.verifyPassword("wrong-password"),
            "an incorrect password must not verify");
    }

    @Test
    @DisplayName("usePasswordHasher(null) 被拒絕（Dependency Inversion seam 不接受 null，不改變既有策略）")
    void usePasswordHasher_withNull_isRejected() {
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class, () -> Account.usePasswordHasher(null));
        // The default self-contained hasher stays installed, so existing accounts still verify.
        assertTrue(registeredAlice().verifyPassword("secret1"),
            "a rejected null install must not mutate the active password hasher");
    }
}
