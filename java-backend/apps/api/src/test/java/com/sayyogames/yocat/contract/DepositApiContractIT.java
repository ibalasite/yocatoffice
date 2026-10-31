package com.sayyogames.yocat.contract;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * API contract: deposit BC endpoints (API.md §3 BC deposit / openapi.yaml).
 * Endpoints: GET /deposit/packs, POST /deposit/checkout.
 */
class DepositApiContractIT extends ApiContractSupport {

    private static final String IDEMPOTENCY_KEY = "7d793037-a076-4c05-9a70-b7e8b2e3c4f5";

    // ── GET /deposit/packs ────────────────────────────────────────────
    @Test
    void packs_happy_returnsCatalog() throws Exception {
        HttpResponse<String> resp = get("/deposit/packs"); // public endpoint
        assertEquals(200, resp.statusCode());
        JsonNode packs = json(resp).path("packs");
        assertTrue(packs.isArray() && packs.size() > 0, "packs catalog non-empty");
        JsonNode first = packs.get(0);
        assertTrue(first.hasNonNull("packId"));
        assertTrue(first.hasNonNull("currency"));
        assertTrue(first.path("amount").isNumber());
        assertTrue(first.hasNonNull("label"));
    }

    @Test
    void packs_error_serverErrorContractShape() throws Exception {
        // API.md: only documented error is 500 INTERNAL_ERROR on catalog load failure.
        HttpResponse<String> resp = get("/deposit/packs");
        if (resp.statusCode() == 500) {
            assertErrorShape(resp, 500);
        } else {
            assertEquals(200, resp.statusCode());
        }
    }

    // ── POST /deposit/checkout ────────────────────────────────────────
    @Test
    void checkout_happy_returnsConfirmedOrder() throws Exception {
        HttpResponse<String> resp = postJsonAuth("/deposit/checkout",
                "{\"packId\":\"g_big\"}", "Idempotency-Key", IDEMPOTENCY_KEY);
        assertEquals(200, resp.statusCode());
        JsonNode b = json(resp);
        assertTrue(b.hasNonNull("orderId"));
        assertEquals("g_big", b.path("packId").asText());
        assertTrue(b.hasNonNull("currency"));
        assertTrue(b.path("amount").isNumber());
        assertEquals("confirmed", b.path("status").asText(), "Phase A fake payment always confirmed");
    }

    @Test
    void checkout_error_unknownPackReturns400() throws Exception {
        HttpResponse<String> resp = postJsonAuth("/deposit/checkout",
                "{\"packId\":\"does_not_exist\"}", "Idempotency-Key", IDEMPOTENCY_KEY);
        assertErrorShape(resp, 400); // VALIDATION_ERROR: packId not in catalog
    }

    @Test
    void checkout_error_missingTokenReturns401() throws Exception {
        HttpResponse<String> resp = postJson("/deposit/checkout", "{\"packId\":\"g_big\"}");
        assertErrorShape(resp, 401); // UNAUTHORIZED
    }
}
