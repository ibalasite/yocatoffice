package com.sayyogames.yocat.platform.web;

/**
 * &lt;&lt;Public DTO&gt;&gt; the authenticated caller for the current request, resolved once by the platform
 * {@code BearerTokenFilter} (EDD §4.5.2 Infrastructure {@code <<Adapter>>}) from the opaque Bearer session
 * token and exposed to every bounded context's controller through {@link CurrentUserArgumentResolver}.
 *
 * <p>This type lives in the cross-cutting {@code platform} module on purpose: it lets a wallet / deposit /
 * entitlement / record / leaderboard / game-save controller obtain the authenticated identity without
 * importing the member BC's internal {@code member.api} types. That removes the per-controller
 * member coupling that previously made the module dependency graph cyclic (EDD §3.4 HC-5 DAG), while the
 * single source of truth for token→account resolution stays the member BC ({@code SessionAuthenticator},
 * invoked once by the filter).</p>
 *
 * @param accountId   the authenticated account's BIGSERIAL identity
 * @param displayName the account's current display name (attribution snapshot, e.g. leaderboard 歸戶)
 * @param email       the account's email
 */
public record CurrentUser(long accountId, String displayName, String email) {

    /** Servlet request attribute under which the filter publishes the resolved principal. */
    public static final String REQUEST_ATTRIBUTE = CurrentUser.class.getName();
}
