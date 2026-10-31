package com.sayyogames.yocat.member.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sayyogames.yocat.shared.AccountId;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the Session entity (EDD §4.5.2 class-domain, §4.5.6 state-machine-session;
 * CONSTANTS §8 SESSION_TTL_DAYS=30). Pure domain — no Spring, no JDBC.
 *
 * <p>RED phase: Session.isExpired is realised by domain-entities (O-01); these tests assert the
 * target behaviour and therefore FAIL until that GREEN step lands.
 *
 * <p>Traceability: TC-UNIT-SESSION-B01 (RTM §3.2 — session TTL 到期邊界 expires_at &lt;= now 判定為過期).
 */
class SessionTest {

    private static final AccountId ALICE = AccountId.of(1001L);
    private static final Instant NOW = Instant.parse("2026-10-30T08:00:00Z");

    private Session sessionExpiringAt(Instant expiresAt) {
        return Session.of("3f8c1d2a0000000000000000000000e9b7", ALICE, expiresAt);
    }

    @Test
    @DisplayName("未到期的 session（expires_at > now）判定為有效")
    void isExpired_beforeExpiry_isFalse() {
        Session session = sessionExpiringAt(NOW.plus(Duration.ofDays(30)));

        assertFalse(session.isExpired(NOW), "a session 30 days ahead is not expired");
    }

    @Test
    @DisplayName("TC-UNIT-SESSION-B01: 到期邊界（expires_at == now）判定為過期")
    void isExpired_atExpiryBoundary_isTrue() {
        Session session = sessionExpiringAt(NOW);

        assertTrue(session.isExpired(NOW), "expires_at <= now is expired (boundary is inclusive)");
    }

    @Test
    @DisplayName("已過期的 session（expires_at < now）判定為過期")
    void isExpired_afterExpiry_isTrue() {
        Session session = sessionExpiringAt(NOW.minus(Duration.ofSeconds(1)));

        assertTrue(session.isExpired(NOW));
    }

    @Test
    @DisplayName("session identity 契約：equals/hashCode 以 opaque token 為依據（EDD §4.5.2）")
    void equalsAndHashCode_tokenIdentity() {
        Session session = sessionExpiringAt(NOW.plus(Duration.ofDays(30)));
        Session sameToken = Session.of(session.token(), AccountId.of(9999L), NOW);
        Session otherToken = Session.of("00000000000000000000000000000000ff", ALICE, session.expiresAt());

        org.junit.jupiter.api.Assertions.assertAll(
            () -> assertTrue(session.equals(session), "same reference is equal"),
            () -> assertTrue(session.equals(sameToken), "same token identifies the same session"),
            () -> org.junit.jupiter.api.Assertions.assertEquals(session.hashCode(), sameToken.hashCode(), "equal identity shares hashCode"),
            () -> assertFalse(session.equals(otherToken), "different token is a different session"),
            () -> assertFalse(session.equals(null), "never equal to null"),
            () -> assertFalse(session.equals("token"), "never equal to a different type"),
            () -> org.junit.jupiter.api.Assertions.assertEquals(ALICE, session.accountId(), "owner is retained")
        );
    }
}
