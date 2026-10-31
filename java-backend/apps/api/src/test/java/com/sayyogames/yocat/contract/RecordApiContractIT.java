package com.sayyogames.yocat.contract;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * API contract: record BC endpoints (API.md §3 BC record / openapi.yaml).
 * Endpoints: POST /records, GET /records.
 */
class RecordApiContractIT extends ApiContractSupport {

    // ── POST /records ─────────────────────────────────────────────────
    @Test
    void create_happy_returnsOkFlag() throws Exception {
        HttpResponse<String> resp = postJsonAuth("/records",
                "{\"type\":\"bankruptcy\",\"payload\":{\"day\":42,\"finalCash\":-15200,\"reason\":\"資金鏈斷裂\"}}");
        assertEquals(200, resp.statusCode());
        assertTrue(json(resp).path("ok").asBoolean());
    }

    @Test
    void create_error_invalidTypeReturns400() throws Exception {
        HttpResponse<String> resp = postJsonAuth("/records",
                "{\"type\":\"not_a_type\",\"payload\":{}}");
        assertErrorShape(resp, 400); // VALIDATION_ERROR: type not in {bankruptcy,achievement,legacy}
    }

    @Test
    void create_error_missingTokenReturns401() throws Exception {
        HttpResponse<String> resp = postJson("/records", "{\"type\":\"legacy\"}");
        assertErrorShape(resp, 401); // UNAUTHORIZED
    }

    // ── GET /records ──────────────────────────────────────────────────
    @Test
    void list_happy_returnsRecordsArray() throws Exception {
        HttpResponse<String> resp = getAuth("/records");
        assertEquals(200, resp.statusCode());
        JsonNode records = json(resp).path("records");
        assertTrue(records.isArray(), "records is an array (possibly empty)");
    }

    @Test
    void list_happy_filterByTypeReturnsArray() throws Exception {
        HttpResponse<String> resp = getAuth("/records?type=bankruptcy");
        assertEquals(200, resp.statusCode());
        assertTrue(json(resp).path("records").isArray());
    }

    @Test
    void list_error_missingTokenReturns401() throws Exception {
        HttpResponse<String> resp = get("/records");
        assertErrorShape(resp, 401); // UNAUTHORIZED
    }
}
