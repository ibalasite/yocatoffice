package com.sayyogames.yocat.member.api;

/**
 * &lt;&lt;Public DTO&gt;&gt; the authenticated caller resolved from an opaque Bearer session token (member BC
 * Public NamedInterface {@code api}). Exposed so any BC's controller can identify the current account
 * without reaching into member internals (EDD §4.1 / §4.2). Carries the stable {@code accountId} plus the
 * {@code displayName} snapshot used for attribution (e.g. leaderboard归戶).
 *
 * @param accountId   the authenticated account's BIGSERIAL identity
 * @param displayName the account's current display name
 * @param email       the account's email
 */
public record AuthPrincipal(long accountId, String displayName, String email) {
}
