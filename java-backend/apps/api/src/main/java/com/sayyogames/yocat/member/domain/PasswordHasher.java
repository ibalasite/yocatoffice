package com.sayyogames.yocat.member.domain;

/**
 * Domain port — password hashing / verification strategy (Dependency Inversion seam for
 * EDD §4.7 Algorithm 3 scrypt). Keeps the {@link Account} aggregate free of any concrete
 * cryptography or infrastructure dependency: the member BC depends on this abstraction, and the
 * infrastructure layer plugs in {@code ScryptPasswordEncoder} (EDD §4.7, member/infra) via
 * {@link Account#usePasswordHasher(PasswordHasher)}.
 *
 * <p>Stored credential format is {@code saltHex$hashHex} (EDD §4.7: salt + derived key, hex-encoded).
 */
public interface PasswordHasher {

    /** Derives a stored credential ({@code saltHex$hashHex}) from a raw password. */
    String encode(String rawPassword);

    /**
     * Constant-time check of a raw password against a previously stored credential.
     * Returns {@code false} for null inputs or a malformed credential rather than throwing.
     */
    boolean matches(String rawPassword, String storedCredential);
}
