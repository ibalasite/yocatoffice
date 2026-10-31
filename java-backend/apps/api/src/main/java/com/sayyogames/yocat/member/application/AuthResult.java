package com.sayyogames.yocat.member.application;

/**
 * &lt;&lt;DTO&gt;&gt; output of {@link AuthService} register / login — the issued opaque session token plus the
 * account identity fields the presentation layer renders (API §3 member AuthResponse). The aggregated wallet
 * view is assembled by the controller from {@code wallet.api.WalletService} after the session is issued.
 *
 * @param token       the opaque Bearer session token (64 hex chars)
 * @param accountId   the account's BIGSERIAL identity
 * @param displayName the account display name
 * @param email       the normalised account email
 */
public record AuthResult(String token, long accountId, String displayName, String email) {
}
