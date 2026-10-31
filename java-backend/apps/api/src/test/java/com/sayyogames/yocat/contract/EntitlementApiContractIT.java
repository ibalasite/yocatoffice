package com.sayyogames.yocat.contract;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * API contract: entitlement BC endpoints (API.md §3 BC entitlement / openapi.yaml).
 * Endpoints: GET /entitlements, POST /entitlements/buy.
 */
class EntitlementApiContractIT extends ApiContractSupport {

    // ── GET /entitlements ─────────────────────────────────────────────
    @Test
    void list_happy_returnsEntitlementsArray() throws Exception {
        HttpResponse<String> resp = getAuth("/entitlements");
        assertEquals(200, resp.statusCode());
        assertTrue(json(resp).path("entitlements").isArray(),
                "entitlements is an array (possibly empty)");
    }

    @Test
    void list_error_missingTokenReturns401() throws Exception {
        HttpResponse<String> resp = get("/entitlements");
        assertErrorShape(resp, 401); // UNAUTHORIZED
    }

    // ── POST /entitlements/buy ────────────────────────────────────────
    @Test
    void buy_happy_returnsWalletAndEntitlements() throws Exception {
        HttpResponse<String> resp = postJsonAuth("/entitlements/buy",
                "{\"kind\":\"cosmetic\",\"itemId\":\"suit_gold\",\"currency\":\"gems\",\"amount\":120}");
        assertEquals(200, resp.statusCode());
        JsonNode b = json(resp);
        assertTrue(b.path("ok").asBoolean());
        assertTrue(b.path("wallet").has("cash") && b.path("wallet").has("gems"));
        assertTrue(b.path("entitlements").isArray());
    }

    @Test
    void buy_error_alreadyOwnedReturns409() throws Exception {
        HttpResponse<String> resp = postJsonAuth("/entitlements/buy",
                "{\"kind\":\"cosmetic\",\"itemId\":\"suit_gold\",\"currency\":\"gems\",\"amount\":120}");
        assertErrorShape(resp, 409); // CONFLICT: UNIQUE(account_id, kind, item_id)
    }

    @Test
    void buy_error_insufficientBalanceReturns402WithWallet() throws Exception {
        HttpResponse<String> resp = postJsonAuth("/entitlements/buy",
                "{\"kind\":\"dream\",\"itemId\":\"soul_einstein\",\"currency\":\"gems\",\"amount\":99999999}");
        assertErrorShape(resp, 402); // PAYMENT_REQUIRED
        assertTrue(json(resp).path("wallet").has("gems"),
                "402 body returns current wallet (transaction rolled back)");
    }

    @Test
    void buy_error_invalidKindReturns400() throws Exception {
        HttpResponse<String> resp = postJsonAuth("/entitlements/buy",
                "{\"kind\":\"not_a_kind\",\"itemId\":\"x\"}");
        assertErrorShape(resp, 400); // VALIDATION_ERROR
    }
}
