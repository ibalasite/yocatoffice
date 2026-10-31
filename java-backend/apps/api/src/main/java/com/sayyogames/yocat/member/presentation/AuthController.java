package com.sayyogames.yocat.member.presentation;

import com.sayyogames.yocat.entitlement.api.OwnedEntitlementsQuery;
import com.sayyogames.yocat.member.api.AuthPrincipal;
import com.sayyogames.yocat.member.api.SessionAuthenticator;
import com.sayyogames.yocat.member.application.AuthResult;
import com.sayyogames.yocat.member.application.AuthService;
import com.sayyogames.yocat.platform.web.UnauthorizedException;
import com.sayyogames.yocat.wallet.application.WalletView;
import com.sayyogames.yocat.wallet.api.WalletService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * member BC presentation layer — identity &amp; session (API §3 member:
 * {@code POST /auth/register|login|logout}, {@code GET /auth/me}).
 *
 * <p>Thin controller: parses input, delegates to {@link AuthService} (register/login/logout) and assembles the
 * aggregated {@code /auth/me} view from the wallet and entitlement BCs' Public NamedInterfaces
 * ({@code WalletService}, {@code OwnedEntitlementsQuery}). No business logic here (EDD §4.5.2). Authentication
 * for {@code /auth/me} is resolved via {@link SessionAuthenticator} (opaque token → account) until the
 * dedicated BearerTokenFilter (contract-middleware) exists.</p>
 */
@RestController
@RequestMapping(path = "/auth", produces = "application/json")
public class AuthController {

    private final AuthService authService;
    private final SessionAuthenticator authenticator;
    private final WalletService walletService;
    private final OwnedEntitlementsQuery ownedEntitlements;

    public AuthController(AuthService authService, SessionAuthenticator authenticator,
                         WalletService walletService, OwnedEntitlementsQuery ownedEntitlements) {
        this.authService = authService;
        this.authenticator = authenticator;
        this.walletService = walletService;
        this.ownedEntitlements = ownedEntitlements;
    }

    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody Map<String, Object> body) {
        AuthResult result = authService.register(
                str(body, "email"), str(body, "displayName"), str(body, "password"));
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("token", result.token());
        resp.put("account", account(result.accountId(), result.email(), result.displayName()));
        resp.put("wallet", wallet(0L, 0L));  // a freshly registered wallet always opens at zero (EDD §7.1)
        return resp;
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, Object> body) {
        AuthResult result = authService.login(str(body, "email"), str(body, "password"));
        WalletView w = walletService.findWallet(com.sayyogames.yocat.shared.AccountId.of(result.accountId()));
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("token", result.token());
        resp.put("account", account(result.accountId(), result.email(), result.displayName()));
        resp.put("wallet", wallet(w.cash(), w.gems()));
        return resp;
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth) {
        // Idempotent: delete whatever session the (possibly absent / invalid) token names; never 401.
        SessionAuthenticator.extractToken(auth).ifPresent(authService::logout);
        return Map.of("ok", true);
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth) {
        AuthPrincipal user = authenticator.authenticate(auth)
                .orElseThrow(() -> new UnauthorizedException("請先登入"));
        WalletView w = walletService.findWallet(com.sayyogames.yocat.shared.AccountId.of(user.accountId()));
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("account", account(user.accountId(), user.email(), user.displayName()));
        resp.put("wallet", wallet(w.cash(), w.gems()));
        resp.put("entitlements", ownedEntitlements.list(user.accountId()));
        return ResponseEntity.ok(resp);
    }

    private static Map<String, Object> account(long id, String email, String displayName) {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("id", id);
        a.put("email", email);
        a.put("display_name", displayName);
        return a;
    }

    private static Map<String, Object> wallet(long cash, long gems) {
        Map<String, Object> w = new LinkedHashMap<>();
        w.put("cash", cash);
        w.put("gems", gems);
        return w;
    }

    private static String str(Map<String, Object> body, String key) {
        Object v = body == null ? null : body.get(key);
        return v == null ? null : v.toString();
    }
}
