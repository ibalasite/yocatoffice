package com.sayyogames.yocat.member.application;

import com.sayyogames.yocat.shared.AccountId;

/**
 * &lt;&lt;DTO&gt;&gt; output of {@link RegisterMemberUseCase} (EDD §4.5.2 class-application, §4.5.4.5).
 *
 * <p>Carries the newly-created account identity; the session token and aggregated wallet view are
 * assembled at the presentation layer ({@code AuthController}) after the commit.</p>
 */
public record RegisterResult(AccountId accountId) {
}
