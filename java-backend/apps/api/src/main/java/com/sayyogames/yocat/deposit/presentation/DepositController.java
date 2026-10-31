package com.sayyogames.yocat.deposit.presentation;

import com.sayyogames.yocat.deposit.application.DepositCheckoutService;
import com.sayyogames.yocat.deposit.domain.DepositOrder;
import com.sayyogames.yocat.deposit.domain.DepositPack;
import com.sayyogames.yocat.deposit.domain.DepositPackRepository;
import com.sayyogames.yocat.platform.web.CurrentUser;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * deposit BC presentation layer — pack catalog &amp; idempotent checkout (API §3 deposit:
 * {@code GET /deposit/packs} public, {@code POST /deposit/checkout} authenticated + {@code Idempotency-Key}).
 * The catalog is returned as a JSON array; checkout requires an Idempotency-Key header (400 when absent).
 */
@RestController
@RequestMapping(path = "/deposit", produces = "application/json")
public class DepositController {

    private final DepositPackRepository packRepo;
    private final DepositCheckoutService checkoutService;

    public DepositController(DepositPackRepository packRepo, DepositCheckoutService checkoutService) {
        this.packRepo = packRepo;
        this.checkoutService = checkoutService;
    }

    @GetMapping("/packs")
    public List<Map<String, Object>> packs() {
        return packRepo.findAllActive().stream().map(DepositController::packView).toList();
    }

    @PostMapping("/checkout")
    public Map<String, Object> checkout(
            CurrentUser user,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) Map<String, Object> body) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("缺少冪等鍵 (Idempotency-Key)");
        }
        String packId = body == null ? null : str(body, "packId");

        DepositOrder order = checkoutService.checkout(user.accountId(), packId, idempotencyKey);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("orderId", order.orderId());
        resp.put("packId", order.packId());
        resp.put("currency", order.currency());
        resp.put("amount", order.amount());
        resp.put("status", order.status());
        return resp;
    }

    private static Map<String, Object> packView(DepositPack p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("packId", p.packId());
        m.put("currency", p.currency());
        m.put("amount", p.amount());
        m.put("label", p.label());
        if (p.bonusPct() != null) {
            m.put("bonusPct", p.bonusPct());
        }
        return m;
    }

    private static String str(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v == null ? null : v.toString();
    }
}
