package com.sayyogames.yocat.member.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Deterministic, dependency-free default {@link PasswordHasher} for the member domain.
 *
 * <p>Uses only the JDK (no Spring, no JDBC, no third-party crypto): a 16-byte random salt
 * (CONSTANTS §8 SCRYPT_SALT_BYTES=16) and a salted SHA-256 digest, hex-encoded as
 * {@code saltHex$hashHex}, with constant-time comparison on verify (PRD §5.1 AC-5 timingSafeEqual).
 *
 * <p>This is the domain's self-contained default so the aggregate is testable without
 * infrastructure. Production replaces it via {@link Account#usePasswordHasher(PasswordHasher)} with
 * the infrastructure {@code ScryptPasswordEncoder} (EDD §4.7 Algorithm 3), which is the
 * cost-hardened production hash.
 */
public final class Sha256PasswordHasher implements PasswordHasher {

    private static final SecureRandom RNG = new SecureRandom();
    private static final int SALT_BYTES = 16;

    @Override
    public String encode(String rawPassword) {
        byte[] salt = new byte[SALT_BYTES];
        RNG.nextBytes(salt);
        String saltHex = HexFormat.of().formatHex(salt);
        return saltHex + "$" + hashHex(saltHex, rawPassword);
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
        String saltHex = storedCredential.substring(0, sep);
        String expectedHashHex = storedCredential.substring(sep + 1);
        String actualHashHex = hashHex(saltHex, rawPassword);
        return constantTimeEquals(actualHashHex, expectedHashHex);
    }

    /** Hex-encoded SHA-256 over {@code saltBytes || utf8(raw)}. Deterministic for a given salt. */
    public static String hashHex(String saltHex, String rawPassword) {
        byte[] salt = HexFormat.of().parseHex(saltHex);
        byte[] message = rawPassword.getBytes(StandardCharsets.UTF_8);
        byte[] buffer = new byte[salt.length + message.length];
        System.arraycopy(salt, 0, buffer, 0, salt.length);
        System.arraycopy(message, 0, buffer, salt.length, message.length);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(buffer));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 must be available on every JVM", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        byte[] x = a.getBytes(StandardCharsets.UTF_8);
        byte[] y = b.getBytes(StandardCharsets.UTF_8);
        if (x.length != y.length) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < x.length; i++) {
            result |= x[i] ^ y[i];
        }
        return result == 0;
    }
}
