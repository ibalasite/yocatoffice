package com.sayyogames.yocat.member.infrastructure;

import java.security.SecureRandom;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * &lt;&lt;Adapter&gt;&gt; Infrastructure generator of opaque session tokens (EDD §5.1 "opaque, 32B hex";
 * §9.1 / ADR-003 Opaque Session Token; §7 Spoofing "32B 高熵 token").
 *
 * <p>Produces a cryptographically strong, high-entropy opaque token of
 * CONSTANTS §8 {@code SESSION_TOKEN_BYTES}=32 random bytes, hex-encoded to 64 lowercase hex characters.
 * The token is the identity of a {@code member.domain.Session} (TTL 30 days,
 * CONSTANTS §8 {@code SESSION_TTL_DAYS}); it carries no claims (opaque), so revocation is immediate by
 * deleting the session row — the login use case / {@code MemberService.login} (EDD §4.2) issues one per
 * successful authentication.
 */
@Component
public class SessionTokenGenerator {

    /** Random token length in bytes (CONSTANTS §8 SESSION_TOKEN_BYTES); 32B → 64 hex chars. */
    private static final int TOKEN_BYTES = 32;

    private final SecureRandom rng = new SecureRandom();

    /** Generates a fresh opaque session token: 64 lowercase hex characters (32 random bytes). */
    public String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        rng.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
