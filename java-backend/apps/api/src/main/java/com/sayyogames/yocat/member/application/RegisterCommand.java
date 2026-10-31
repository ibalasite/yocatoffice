package com.sayyogames.yocat.member.application;

/**
 * &lt;&lt;DTO&gt;&gt; input command for {@link RegisterMemberUseCase} (EDD §4.5.2 class-application, §4.5.4.5).
 *
 * <p>Maps {@code POST /auth/register} body {email, displayName, password}. Field-level validation
 * (email format, displayName 1–24, password ≥6) is enforced by the domain value objects.</p>
 */
public record RegisterCommand(String email, String displayName, String password) {
}
