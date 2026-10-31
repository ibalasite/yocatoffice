package com.sayyogames.yocat.member.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.sayyogames.yocat.member.domain.PasswordHasher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the scrypt {@link PasswordHasher} adapter (EDD §4.7 Algorithm 3; RTM §3.2
 * TC-UNIT-SCRYPTENC-E01). Pure JUnit — no Spring context, no DB (the adapter is self-contained crypto).
 */
class ScryptPasswordEncoderTest {

    private final PasswordHasher encoder = new ScryptPasswordEncoder();

    @Test
    @DisplayName("encode 產生 saltHex(32)$hashHex(128) 格式（salt 16B、key 64B）")
    void encodeProducesSaltDollarHashHexFormat() {
        String stored = encoder.encode("secret1");
        assertThat(stored).matches("^[0-9a-f]{32}\\$[0-9a-f]{128}$");
    }

    @Test
    @DisplayName("同一密碼兩次 encode 因隨機 salt 而不同")
    void encodeIsSaltedSoTwoHashesDiffer() {
        assertThat(encoder.encode("secret1")).isNotEqualTo(encoder.encode("secret1"));
    }

    @Test
    @DisplayName("matches 對正確密碼回 true")
    void matchesReturnsTrueForCorrectPassword() {
        String stored = encoder.encode("secret1");
        assertThat(encoder.matches("secret1", stored)).isTrue();
    }

    @Test
    @DisplayName("TC-UNIT-SCRYPTENC-E01：錯誤密碼比對失敗（constant-time）")
    void matchesReturnsFalseForWrongPassword() {
        String stored = encoder.encode("secret1");
        assertThat(encoder.matches("wrong-password", stored)).isFalse();
    }

    @Test
    @DisplayName("matches 對 null/格式錯誤的憑證回 false 而非拋例外")
    void matchesReturnsFalseForNullOrMalformedCredential() {
        String stored = encoder.encode("secret1");
        assertThat(encoder.matches(null, stored)).isFalse();
        assertThat(encoder.matches("secret1", null)).isFalse();
        assertThat(encoder.matches("secret1", "no-separator")).isFalse();
        assertThat(encoder.matches("secret1", "$onlyhash")).isFalse();
        assertThat(encoder.matches("secret1", "zz$zz")).isFalse();
    }
}
