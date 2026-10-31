package com.sayyogames.yocat.member.infrastructure;

import com.sayyogames.yocat.member.domain.PasswordHasher;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import org.bouncycastle.crypto.generators.SCrypt;
import org.springframework.stereotype.Component;

/**
 * &lt;&lt;Adapter&gt;&gt; Infrastructure implementation of the member-domain {@link PasswordHasher} port
 * (EDD §4.5.2 class-application {@code ScryptPasswordEncoder}, §4.7 Algorithm 3). The password BC
 * depends only on the domain abstraction; this adapter plugs in the cost-hardened scrypt hash.
 *
 * <p>Hashing uses scrypt (Bouncy Castle {@link SCrypt}) with a 16-byte random salt
 * (CONSTANTS §8 {@code SCRYPT_SALT_BYTES}=16) and a 64-byte derived key
 * (CONSTANTS §8 {@code SCRYPT_KEYLEN}=64), encoded as {@code saltHex$hashHex} — the stored credential
 * format of EDD §4.7 and SCHEMA {@code member.accounts.pass_hash}. Verification is constant-time
 * ({@link MessageDigest#isEqual} / {@code timingSafeEqual}) to resist timing attacks (PRD §5.1 AC-5).
 *
 * <p>scrypt cost parameters (N/r/p) are not pinned by EDD/CONSTANTS; the values below follow the
 * OWASP / Spring {@code SCryptPasswordEncoder} baseline ({@code N=2^14, r=8, p=1}) for interactive
 * login, deliberately expensive (EDD §4.7 複雜度 "刻意高成本").
 */
@Component
public class ScryptPasswordEncoder implements PasswordHasher {

    /** Random salt length in bytes (CONSTANTS §8 SCRYPT_SALT_BYTES). */
    private static final int SALT_BYTES = 16;
    /** Derived-key length in bytes (CONSTANTS §8 SCRYPT_KEYLEN). */
    private static final int KEY_LEN = 64;
    /** scrypt CPU/memory cost — must be a power of two (OWASP/Spring baseline 2^14). */
    private static final int COST_N = 16384;
    /** scrypt block-size parameter (OWASP/Spring baseline). */
    private static final int BLOCK_SIZE_R = 8;
    /** scrypt parallelisation parameter (OWASP/Spring baseline). */
    private static final int PARALLELISM_P = 1;

    private static final SecureRandom RNG = new SecureRandom();

    @Override
    public String encode(String rawPassword) {
        if (rawPassword == null) {
            throw new IllegalArgumentException("rawPassword 不可為 null");
        }
        byte[] salt = new byte[SALT_BYTES];
        RNG.nextBytes(salt);
        byte[] hash = scrypt(rawPassword, salt);
        HexFormat hex = HexFormat.of();
        return hex.formatHex(salt) + "$" + hex.formatHex(hash);
    }

    @Override
    public boolean matches(String rawPassword, String storedCredential) {
        if (rawPassword == null || storedCredential == null) {
            return false;
        }
        int sep = storedCredential.indexOf('$');
        if (sep <= 0 || sep == storedCredential.length() - 1) {
            return false;
        }
        byte[] salt;
        byte[] expected;
        try {
            HexFormat hex = HexFormat.of();
            salt = hex.parseHex(storedCredential.substring(0, sep));
            expected = hex.parseHex(storedCredential.substring(sep + 1));
        } catch (IllegalArgumentException malformed) {
            return false;
        }
        byte[] actual = scrypt(rawPassword, salt);
        return MessageDigest.isEqual(actual, expected);
    }

    private static byte[] scrypt(String rawPassword, byte[] salt) {
        return SCrypt.generate(
                rawPassword.getBytes(StandardCharsets.UTF_8), salt,
                COST_N, BLOCK_SIZE_R, PARALLELISM_P, KEY_LEN);
    }
}
