package com.sayyogames.yocat.member.domain;

import java.util.Optional;

/**
 * &lt;&lt;Repository&gt;&gt; port for the {@link Session} entity (member BC, SCHEMA §3.1.2 {@code member.sessions}).
 *
 * <p>Domain-owned interface; the JDBC adapter lives in infrastructure and is injected via DI (DIP).
 * An opaque Bearer token is the identity; a session is valid until {@code expires_at} (TTL 30 days,
 * CONSTANTS §8). Logout deletes the row (immediate revocation, ADR-003).</p>
 */
public interface SessionRepository {

    /** Persist a freshly issued session (INSERT token, account_id, expires_at). Caller: login / register. */
    void save(Session session);

    /** Resolve a session by its opaque token; empty when the token is unknown. Caller: SessionAuthenticator. */
    Optional<Session> findByToken(String token);

    /** Delete the session row for a token (idempotent — no error when absent). Caller: logout. */
    void deleteByToken(String token);
}
