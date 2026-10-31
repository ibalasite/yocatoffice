package com.sayyogames.yocat.member.api;

import com.sayyogames.yocat.member.domain.Account;
import com.sayyogames.yocat.member.domain.MemberRepository;
import com.sayyogames.yocat.member.domain.Session;
import com.sayyogames.yocat.member.domain.SessionRepository;
import com.sayyogames.yocat.platform.web.UnauthorizedException;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * &lt;&lt;ApplicationService&gt;&gt; member BC Public NamedInterface {@code api} — resolves an opaque Bearer session
 * token to the authenticated {@link AuthPrincipal} (EDD §4.2 {@code MemberService.verifySession}; §5.1 opaque
 * session). Consumed cross-BC by every controller that guards an authenticated endpoint, exactly like
 * {@code wallet.api.WalletService} is consumed by entitlement (EDD §4.1 / §4.3 module DAG).
 *
 * <p>Note (EDD §9.1): the authoritative token {@code BearerTokenFilter} (Spring Security filter chain) is
 * produced by the downstream {@code contract-middleware} sub-step (O-09). This service is the token→account
 * resolution that the controllers call directly until the filter exists; the filter will delegate to the same
 * resolution so the trust model (opaque token, expiry, immediate revocation) stays single-sourced.</p>
 */
@Service
public class SessionAuthenticator {

    private static final String BEARER_PREFIX = "Bearer ";

    private final SessionRepository sessionRepo;
    private final MemberRepository memberRepo;

    public SessionAuthenticator(SessionRepository sessionRepo, MemberRepository memberRepo) {
        this.sessionRepo = sessionRepo;
        this.memberRepo = memberRepo;
    }

    /** Extracts the raw token from an {@code Authorization: Bearer <token>} header value; empty when absent. */
    public static Optional<String> extractToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }
        String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? Optional.empty() : Optional.of(token);
    }

    /**
     * Resolve an {@code Authorization} header to the authenticated principal; empty when the token is
     * missing, unknown, or expired (→ the controller maps empty to 401). Read-only.
     */
    @Transactional(readOnly = true)
    public Optional<AuthPrincipal> authenticate(String authorizationHeader) {
        return extractToken(authorizationHeader).flatMap(this::resolve);
    }

    /**
     * Resolve the authenticated principal or fail with 401 (API §5.2 {@code "請先登入"}). The single guard every
     * authenticated endpoint uses until the BearerTokenFilter (contract-middleware) is in place.
     */
    public AuthPrincipal require(String authorizationHeader) {
        return authenticate(authorizationHeader)
                .orElseThrow(() -> new UnauthorizedException("請先登入"));
    }

    private Optional<AuthPrincipal> resolve(String token) {
        Optional<Session> session = sessionRepo.findByToken(token);
        if (session.isEmpty() || session.get().isExpired(Instant.now())) {
            return Optional.empty();
        }
        return memberRepo.findById(session.get().accountId())
                .map(SessionAuthenticator::toPrincipal);
    }

    private static AuthPrincipal toPrincipal(Account account) {
        return new AuthPrincipal(account.id().value(), account.displayName().value(), account.email().value());
    }
}
