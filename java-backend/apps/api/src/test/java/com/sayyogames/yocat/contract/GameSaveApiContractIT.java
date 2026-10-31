package com.sayyogames.yocat.contract;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * API contract: game-save BC endpoints (API.md §3 BC game-save / openapi.yaml).
 * Endpoints: GET /save, PUT /save.
 */
class GameSaveApiContractIT extends ApiContractSupport {

    // ── GET /save ─────────────────────────────────────────────────────
    @Test
    void get_happy_returnsSaveEnvelope() throws Exception {
        HttpResponse<String> resp = getAuth("/save?slot=main");
        assertEquals(200, resp.statusCode());
        JsonNode b = json(resp);
        assertTrue(b.has("save"), "response always has a 'save' key (null when empty)");
        JsonNode save = b.path("save");
        if (!save.isNull()) {
            assertTrue(save.has("data"), "save.data present when a save exists");
            assertTrue(save.hasNonNull("updatedAt"));
        }
    }

    @Test
    void get_happy_noSaveReturnsNull() throws Exception {
        HttpResponse<String> resp = getAuth("/save?slot=empty_slot");
        assertEquals(200, resp.statusCode());
        // API.md: {"save": null} when the slot has no save — the key must still exist.
        assertTrue(json(resp).has("save"));
    }

    @Test
    void get_error_missingTokenReturns401() throws Exception {
        HttpResponse<String> resp = get("/save");
        assertErrorShape(resp, 401); // UNAUTHORIZED
    }

    // ── PUT /save ─────────────────────────────────────────────────────
    @Test
    void put_happy_returnsOkFlag() throws Exception {
        HttpResponse<String> resp = putJsonAuth("/save",
                "{\"slot\":\"main\",\"data\":{\"day\":42,\"cash\":18750,\"morale\":70,\"roster\":[\"m1\",\"m2\"]}}");
        assertEquals(200, resp.statusCode());
        assertTrue(json(resp).path("ok").asBoolean());
    }

    @Test
    void put_error_missingDataReturns400() throws Exception {
        HttpResponse<String> resp = putJsonAuth("/save", "{\"slot\":\"main\"}");
        assertErrorShape(resp, 400); // VALIDATION_ERROR: 缺 data
    }

    @Test
    void put_error_missingTokenReturns401() throws Exception {
        HttpResponse<String> resp = putJson("/save", "{\"data\":{\"day\":1}}");
        assertErrorShape(resp, 401); // UNAUTHORIZED
    }
}
