package com.sayyogames.yocat.member.application;

import com.sayyogames.yocat.member.domain.Account;
import com.sayyogames.yocat.member.domain.Email;
import com.sayyogames.yocat.member.domain.MemberRepository;
import com.sayyogames.yocat.member.domain.PasswordHasher;
import com.sayyogames.yocat.member.domain.Session;
import com.sayyogames.yocat.member.domain.SessionRepository;
import com.sayyogames.yocat.member.infrastructure.SessionTokenGenerator;
import com.sayyogames.yocat.platform.web.UnauthorizedException;
import com.sayyogames.yocat.shared.AccountId;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * &lt;&lt;ApplicationService&gt;&gt; member BC identity + session orchestration (EDD §4.2 MemberService; API §3 member).
 *
 * <p>Transactional composition root for {@code POST /auth/register|login|logout}: registration runs
 * {@link RegisterMemberUseCase} (which validates, persists the account and publishes {@code MemberRegistered}
 * so the wallet BC opens the initial wallet) and then issues an opaque 30-day session; login verifies the
 * credential through the {@link PasswordHasher} port (scrypt in production) in constant time and issues a new
 * session, returning the <em>same</em> generic error whether the email is unknown or the password is wrong
 * (no account enumeration, API §5.2); logout deletes the session row (idempotent, immediate revocation).</p>
 */
@Service
public class AuthService {

    /** Session lifetime (CONSTANTS §8 SESSION_TTL_DAYS=30). */
    private static final Duration SESSION_TTL = Duration.ofDays(30);
    /** Generic credential error (API §5.2 — never reveals whether the email exists). */
    private static final String BAD_CREDENTIALS = "email 或密碼錯誤";

    private final RegisterMemberUseCase registerUseCase;
    private final MemberRepository memberRepo;
    private final SessionRepository sessionRepo;
    private final SessionTokenGenerator tokenGenerator;
    private final PasswordHasher passwordHasher;

    public AuthService(RegisterMemberUseCase registerUseCase, MemberRepository memberRepo,
                       SessionRepository sessionRepo, SessionTokenGenerator tokenGenerator,
                       PasswordHasher passwordHasher) {
        this.registerUseCase = registerUseCase;
        this.memberRepo = memberRepo;
        this.sessionRepo = sessionRepo;
        this.tokenGenerator = tokenGenerator;
        this.passwordHasher = passwordHasher;
    }

    /** Register a new account (+ wallet via event) and issue a session. */
    @Transactional
    public AuthResult register(String email, String displayName, String password) {
        RegisterResult result = registerUseCase.execute(new RegisterCommand(email, displayName, password));
        String token = issueSession(result.accountId());
        return new AuthResult(token, result.accountId().value(), displayName, Email.of(email).value());
    }

    /** Authenticate and issue a session; {@link UnauthorizedException} (401) on any credential failure. */
    @Transactional
    public AuthResult login(String email, String password) {
        Optional<Account> found = memberRepo.findByEmail(Email.of(email));
        if (found.isEmpty() || !passwordHasher.matches(password, found.get().passwordHash())) {
            throw new UnauthorizedException(BAD_CREDENTIALS);
        }
        Account account = found.get();
        String token = issueSession(account.id());
        return new AuthResult(token, account.id().value(), account.displayName().value(), account.email().value());
    }

    /** Delete the caller's session (idempotent — never fails on an unknown/expired token). */
    @Transactional
    public void logout(String token) {
        sessionRepo.deleteByToken(token);
    }

    private String issueSession(AccountId accountId) {
        String token = tokenGenerator.newToken();
        sessionRepo.save(Session.of(token, accountId, Instant.now().plus(SESSION_TTL)));
        return token;
    }
}
