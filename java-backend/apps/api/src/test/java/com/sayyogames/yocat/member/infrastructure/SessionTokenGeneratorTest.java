package com.sayyogames.yocat.member.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the opaque {@link SessionTokenGenerator} (EDD §5.1 / §9.1 / ADR-003;
 * CONSTANTS §8 SESSION_TOKEN_BYTES=32). Pure JUnit — self-contained {@code SecureRandom}.
 */
class SessionTokenGeneratorTest {

    private final SessionTokenGenerator generator = new SessionTokenGenerator();

    @Test
    @DisplayName("token 為 64 個小寫 hex 字元（32 random bytes）")
    void tokenIs64LowercaseHexChars() {
        assertThat(generator.newToken()).matches("^[0-9a-f]{64}$");
    }

    @Test
    @DisplayName("高熵：1000 個 token 互不重複")
    void tokensAreUnique() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            assertThat(seen.add(generator.newToken())).isTrue();
        }
    }
}
