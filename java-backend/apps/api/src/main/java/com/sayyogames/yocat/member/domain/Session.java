package com.sayyogames.yocat.member.domain;

import com.sayyogames.yocat.shared.AccountId;
import java.time.Instant;
import java.util.Objects;

/**
 * Entity — an opaque authentication session inside the member aggregate (EDD §4.5.2 class-domain,
 * §4.5.6 state-machine-session). Pure domain — no Spring, no JDBC.
 *
 * <p>Identity is the opaque {@code token}. A session is valid until its {@code expiresAt} instant
 * (issued with a TTL of CONSTANTS §8 SESSION_TTL_DAYS=30); the expiry boundary is inclusive, i.e.
 * {@code expiresAt <= now} is already expired (EDD §4.5.6 state-machine-session ACTIVE→EXPIRED).
 */
public final class Session {

    private final String token;
    private final AccountId accountId;
    private final Instant expiresAt;

    private Session(String token, AccountId accountId, Instant expiresAt) {
        this.token = token;
        this.accountId = accountId;
        this.expiresAt = expiresAt;
    }

    /** Reconstitutes a session from its token, owner and expiry (issue / repository rehydration). */
    public static Session of(String token, AccountId accountId, Instant expiresAt) {
        return new Session(token, accountId, expiresAt);
    }

    public String token() {
        return token;
    }

    public AccountId accountId() {
        return accountId;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    /**
     * True when the session has reached or passed its expiry at {@code now}. The boundary is
     * inclusive ({@code expiresAt <= now} ⇒ expired), matching EDD §4.5.6 state-machine-session.
     */
    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Session other)) {
            return false;
        }
        return this.token.equals(other.token);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(token);
    }
}
