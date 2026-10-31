package com.sayyogames.yocat.wallet.presentation;

import com.sayyogames.yocat.deposit.domain.DepositPack;
import com.sayyogames.yocat.deposit.domain.DepositPackRepository;
import com.sayyogames.yocat.platform.web.CurrentUser;
import com.sayyogames.yocat.shared.AccountId;
import com.sayyogames.yocat.wallet.application.WalletAppService;
import com.sayyogames.yocat.wallet.application.WalletView;
import com.sayyogames.yocat.wallet.domain.Currency;
import com.sayyogames.yocat.wallet.domain.InsufficientBalanceException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * wallet BC presentation layer — server-authoritative wallet (API §3 wallet:
 * {@code POST /wallet/topup}, {@code POST /wallet/spend}). Parses input, resolves the authenticated account,
 * delegates to {@link WalletAppService}; a top-up by predefined {@code pack} resolves its currency/amount from
 * the deposit catalog Public NamedInterface. Insufficient balance is rendered as 402 with the current wallet
 * (API §2.3); no business logic here (EDD §4.5.2). The authenticated caller is injected as {@link CurrentUser}
 * (resolved by the platform BearerTokenFilter); a missing/invalid token yields 401 before the handler runs.
 */
@RestController
@RequestMapping(path = "/wallet", produces = "application/json")
public class WalletController {

    private final WalletAppService walletAppService;
    private final DepositPackRepository packRepo;

    public WalletController(WalletAppService walletAppService, DepositPackRepository packRepo) {
        this.walletAppService = walletAppService;
        this.packRepo = packRepo;
    }

    @PostMapping("/topup")
    public Map<String, Object> topup(CurrentUser user, @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> b = body == null ? Map.of() : body;

        Currency currency;
        long amount;
        String pack = str(b, "pack");
        if (pack != null && !pack.isBlank()) {
            DepositPack dp = packRepo.findByPackId(pack)
                    .orElseThrow(() -> new IllegalArgumentException("儲值包不正確"));
            currency = Currency.valueOf(dp.currency().toUpperCase(Locale.ROOT));
            amount = dp.amount();
        } else {
            currency = currency(str(b, "currency"));
            amount = longVal(b, "amount");
        }

        WalletView w = walletAppService.topUp(AccountId.of(user.accountId()), currency, amount);
        long balance = currency == Currency.GEMS ? w.gems() : w.cash();

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("ok", true);
        resp.put("currency", currency.name().toLowerCase(Locale.ROOT));
        resp.put("amount", amount);
        resp.put("balance", balance);
        resp.put("wallet", wallet(w));
        return resp;
    }

    @PostMapping("/spend")
    public ResponseEntity<Map<String, Object>> spend(
            CurrentUser user, @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> b = body == null ? Map.of() : body;
        Currency currency = currency(str(b, "currency"));
        long amount = longVal(b, "amount");
        String reason = str(b, "reason");
        AccountId accountId = AccountId.of(user.accountId());

        try {
            WalletView w = walletAppService.spend(accountId, currency, amount, reason);
            long balance = currency == Currency.GEMS ? w.gems() : w.cash();
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("ok", true);
            resp.put("currency", currency.name().toLowerCase(Locale.ROOT));
            resp.put("balance", balance);
            resp.put("wallet", wallet(w));
            return ResponseEntity.ok(resp);
        } catch (InsufficientBalanceException insufficient) {
            WalletView w = walletAppService.find(accountId); // transaction rolled back → unchanged balance
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("error", "餘額不足");
            resp.put("currency", currency.name().toLowerCase(Locale.ROOT));
            resp.put("balance", currency == Currency.GEMS ? w.gems() : w.cash());
            resp.put("wallet", wallet(w));
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(resp);
        }
    }

    private static Currency currency(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("金額不正確");
        }
        try {
            return Currency.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("幣別不正確");
        }
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
