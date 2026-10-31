package com.sayyogames.yocat;

import com.sayyogames.yocat.member.api.AuthPrincipal;
import com.sayyogames.yocat.member.api.SessionAuthenticator;
import com.sayyogames.yocat.platform.cache.RateLimitAction;
import com.sayyogames.yocat.platform.cache.RateLimiter;
import com.sayyogames.yocat.platform.web.CurrentUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Central Bearer-token authentication + Redis sliding-window rate-limit filter
 * (EDD §4.5.2 {@code BearerTokenFilter} Infrastructure {@code <<Adapter>>}; EDD §4.5.1 / §11.3C RateLimitFilter;
 * API §Authentication / §Rate Limiting; ARCH §6).
 *
 * <p>Runs once per request, before the DispatcherServlet, and does two cross-cutting jobs so the
 * controllers stay thin and BC-decoupled:</p>
 * <ol>
 *   <li><b>Authentication</b> — resolves the opaque {@code Authorization: Bearer <token>} to the authenticated
 *       account exactly once via the member BC's {@link SessionAuthenticator} (the single source of truth for
 *       token→account, EDD §4.2 / §5.1), and publishes it as a {@link CurrentUser} request attribute that
 *       {@code CurrentUserArgumentResolver} hands to any controller. This replaces each controller importing
 *       {@code member.api} directly, which had made the module dependency graph cyclic (EDD §3.4 HC-5 DAG).</li>
 *   <li><b>Rate limiting</b> — applies the EDD §11.3C sliding-window budget to the five rate-limited endpoint
 *       classes (login / register / wallet / buy / lb) through the platform {@link RateLimiter} (atomic Redis
 *       Lua; fails open on a Redis outage, EDD §11.2). When the window is exceeded it short-circuits with HTTP
 *       429 {@code {"error":"請求過於頻繁"}} + a {@code Retry-After} header (API §5.2 / §6) and never reaches the
 *       controller.</li>
 * </ol>
 *
 * <p>This class lives in the application root package (not a bounded context) because it is the composition
 * seam that legitimately wires the member authenticator and the platform rate limiter together; the root
 * package is the application layer, outside the Spring Modulith module boundaries.</p>
 */
public class BearerTokenFilter extends OncePerRequestFilter {

    private final SessionAuthenticator sessionAuthenticator;
    private final RateLimiter rateLimiter;

    public BearerTokenFilter(SessionAuthenticator sessionAuthenticator, RateLimiter rateLimiter) {
        this.sessionAuthenticator = sessionAuthenticator;
        this.rateLimiter = rateLimiter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        // 1. Authenticate (opaque Bearer → account), publish the principal for the argument resolver.
        Optional<AuthPrincipal> principal =
                sessionAuthenticator.authenticate(request.getHeader(HttpHeaders.AUTHORIZATION));
        principal.ifPresent(p -> request.setAttribute(
                CurrentUser.REQUEST_ATTRIBUTE,
                new CurrentUser(p.accountId(), p.displayName(), p.email())));

        // 2. Rate limit the EDD §11.3C endpoint classes (if this request is one of them).
        RateLimitAction action = actionFor(request);
        if (action != null) {
            String identifier = identifierFor(action, principal, request);
            if (!rateLimiter.allow(action, identifier)) {
                tooManyRequests(response, action);
                return; // short-circuit: the controller is never invoked
            }
        }

        chain.doFilter(request, response);
    }

    /** Maps an HTTP method + path to its EDD §11.3C rate-limit action, or {@code null} when unlimited. */
    private static RateLimitAction actionFor(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getServletPath();
        if (path == null || path.isEmpty()) {
            path = request.getRequestURI();
        }
        if ("POST".equals(method)) {
            switch (path) {
                case "/auth/login":
                    return RateLimitAction.LOGIN;
                case "/auth/register":
                    return RateLimitAction.REGISTER;
                case "/wallet/topup":
                case "/wallet/spend":
                    return RateLimitAction.WALLET;
                case "/entitlements/buy":
                    return RateLimitAction.BUY;
                default:
                    return null;
            }
        }
        if ("GET".equals(method) && "/leaderboard".equals(path)) {
            return RateLimitAction.LEADERBOARD;
        }
        return null;
    }

    /**
     * Per EDD §11.3C: login / register / lb are keyed by client IP; wallet / buy by account id (falling back to
     * IP for an unauthenticated caller, who the controller will subsequently reject with 401). Never trusts a
     * spoofable header — the identifier is the account id or the socket remote address.
     */
    private static String identifierFor(RateLimitAction action, Optional<AuthPrincipal> principal,
                                        HttpServletRequest request) {
        switch (action) {
            case WALLET:
            case BUY:
                return principal.map(p -> "acct:" + p.accountId()).orElseGet(() -> "ip:" + clientIp(request));
            case LOGIN:
            case REGISTER:
            case LEADERBOARD:
            default:
                return "ip:" + clientIp(request);
        }
    }

    private static String clientIp(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        return remote == null ? "unknown" : remote;
    }

    private static void tooManyRequests(HttpServletResponse response, RateLimitAction action) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, Integer.toString(action.windowSeconds()));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                "{\"error\":\"請求過於頻繁\",\"retryAfterSeconds\":" + action.windowSeconds() + "}");
    }
}
