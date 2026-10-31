package com.sayyogames.yocat.entitlement.presentation;

import com.sayyogames.yocat.entitlement.api.OwnedEntitlementsQuery;
import com.sayyogames.yocat.entitlement.application.BuyResult;
import com.sayyogames.yocat.entitlement.application.EntitlementAppService;
import com.sayyogames.yocat.entitlement.domain.Entitlement;
import com.sayyogames.yocat.entitlement.domain.EntitlementKind;
import com.sayyogames.yocat.platform.web.CurrentUser;
import com.sayyogames.yocat.wallet.application.WalletView;
import com.sayyogames.yocat.wallet.api.WalletService;
import com.sayyogames.yocat.wallet.domain.Currency;
import com.sayyogames.yocat.wallet.domain.InsufficientBalanceException;
import com.sayyogames.yocat.shared.AccountId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * entitlement BC presentation layer — atomic buy &amp; listing (API §3 entitlement:
 * {@code POST /entitlements/buy}, {@code GET /entitlements}). Parses/validates input (invalid kind or blank
 * itemId → 400 {@code "項目不正確"}), resolves the authenticated account, delegates to
 * {@link EntitlementAppService}; insufficient balance is rendered as 402 with the current wallet.
 */
@RestController
@RequestMapping(produces = "application/json")
public class EntitlementController {

    private final EntitlementAppService entitlementService;
    private final OwnedEntitlementsQuery ownedEntitlements;
    private final WalletService walletService;

    public EntitlementController(EntitlementAppService entitlementService, OwnedEntitlementsQuery ownedEntitlements,
                                WalletService walletService) {
        this.entitlementService = entitlementService;
        this.ownedEntitlements = ownedEntitlements;
        this.walletService = walletService;
    }

    @GetMapping("/entitlements")
    public List<com.sayyogames.yocat.entitlement.api.OwnedEntitlementView> list(CurrentUser user) {
        return ownedEntitlements.list(user.accountId());
    }

    @PostMapping("/entitlements/buy")
    public ResponseEntity<Map<String, Object>> buy(
            CurrentUser user, @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> b = body == null ? Map.of() : body;
        EntitlementKind kind = kind(str(b, "kind"));
        String itemId = str(b, "itemId");
        if (itemId == null || itemId.isBlank()) {
            throw new IllegalArgumentException("項目不正確");
        }
        Currency currency = currency(str(b, "currency"));
        long amount = longVal(b, "amount");
        AccountId accountId = AccountId.of(user.accountId());

        try {
            BuyResult result = entitlementService.buy(user.accountId(), kind, itemId, currency, amount);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("ok", true);
            resp.put("wallet", wallet(result.wallet()));
            resp.put("entitlements", result.entitlements().stream().map(EntitlementController::entView).toList());
            return ResponseEntity.ok(resp);
        } catch (InsufficientBalanceException insufficient) {
            WalletView w = walletService.findWallet(accountId); // transaction rolled back → unchanged balance
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("error", "餘額不足");
            resp.put("wallet", wallet(w));
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(resp);
        }
    }

    private static EntitlementKind kind(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("項目不正確");
        }
        try {
            return EntitlementKind.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("項目不正確");
        }
    }

    private static Currency currency(String raw) {
        if (raw == null || raw.isBlank()) {
            return Currency.GEMS;
        }
        try {
            return Currency.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            return Currency.GEMS;
        }
    }

    private static Map<String, Object> entView(Entitlement e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", e.kind().name().toLowerCase(Locale.ROOT));
        m.put("itemId", e.itemId());
        return m;
    }

    private static Map<String, Object> wallet(WalletView w) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cash", w.cash());
        m.put("gems", w.gems());
        return m;
    }

    private static String str(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v == null ? null : v.toString();
    }

    private static long longVal(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v instanceof Number n) {
            return n.longValue();
        }
        if (v == null) {
            return 0L;
        }
        try {
            return Long.parseLong(v.toString());
        } catch (NumberFormatException nfe) {
            return 0L;
        }
    }
}
