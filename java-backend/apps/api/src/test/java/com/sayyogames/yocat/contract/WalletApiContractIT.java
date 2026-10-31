package com.sayyogames.yocat.contract;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * API contract: wallet BC endpoints (API.md §3 BC wallet / openapi.yaml).
 * Endpoints: POST /wallet/topup, POST /wallet/spend.
 */
class WalletApiContractIT extends ApiContractSupport {

    // ── POST /wallet/topup ────────────────────────────────────────────
    @Test
    void topup_happy_returnsBalanceAndWallet() throws Exception {
        HttpResponse<String> resp = postJsonAuth("/wallet/topup", "{\"pack\":\"g_mid\"}");
        assertEquals(200, resp.statusCode());
        JsonNode b = json(resp);
        assertTrue(b.path("ok").asBoolean());
        assertTrue(b.hasNonNull("currency"));
        assertTrue(b.path("amount").isNumber(), "credited amount present");
        assertTrue(b.path("balance").isNumber());
        assertTrue(b.path("wallet").has("cash") && b.path("wallet").has("gems"));
    }

    @Test
    void topup_error_amountOutOfRangeReturns400() throws Exception {
        HttpResponse<String> resp = postJsonAuth("/wallet/topup",
                "{\"currency\":\"cash\",\"amount\":0}");
        assertErrorShape(resp, 400); // VALIDATION_ERROR: amount <= 0
    }

    @Test
    void topup_error_missingTokenReturns401() throws Exception {
        HttpResponse<String> resp = postJson("/wallet/topup", "{\"pack\":\"g_mid\"}");
        assertErrorShape(resp, 401); // UNAUTHORIZED
    }

    // ── POST /wallet/spend ────────────────────────────────────────────
    @Test
    void spend_happy_returnsBalanceAndWallet() throws Exception {
        HttpResponse<String> resp = postJsonAuth("/wallet/spend",
                "{\"currency\":\"cash\",\"amount\":1250,\"reason\":\"salary_day_42\"}");
        assertEquals(200, resp.statusCode());
        JsonNode b = json(resp);
        assertTrue(b.path("ok").asBoolean());
        assertEquals("cash", b.path("currency").asText());
        assertTrue(b.path("balance").isNumber());
        assertTrue(b.path("wallet").has("cash") && b.path("wallet").has("gems"));
    }

    @Test
    void spend_error_insufficientBalanceReturns402WithWallet() throws Exception {
        HttpResponse<String> resp = postJsonAuth("/wallet/spend",
                "{\"currency\":\"cash\",\"amount\":99999999,\"reason\":\"overspend\"}");
        assertErrorShape(resp, 402); // PAYMENT_REQUIRED
        assertTrue(json(resp).path("wallet").has("cash"),
                "402 body returns current wallet (transaction rolled back)");
    }

    @Test
    void spend_error_invalidAmountReturns400() throws Exception {
        HttpResponse<String> resp = postJsonAuth("/wallet/spend",
                "{\"currency\":\"cash\",\"amount\":-5}");
        assertErrorShape(resp, 400); // VALIDATION_ERROR
    }
}
