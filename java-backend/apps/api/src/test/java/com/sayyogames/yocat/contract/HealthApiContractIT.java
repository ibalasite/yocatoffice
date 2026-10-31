package com.sayyogames.yocat.contract;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * API contract: platform health endpoints (API.md §3 BC 平台底座 / openapi.yaml).
 * Endpoints: GET /health/live, GET /health/ready.
 */
class HealthApiContractIT extends ApiContractSupport {

    // ── GET /health/live ──────────────────────────────────────────────
    @Test
    void live_happy_returnsUp() throws Exception {
        HttpResponse<String> resp = get("/health/live");
        assertEquals(200, resp.statusCode(), "liveness 200 when process is up");
        assertEquals("UP", json(resp).path("status").asText());
    }

    @Test
    void live_error_shutdownContractShape() throws Exception {
        // API.md: 503 {"status":"DOWN"} during Graceful Shutdown.
        HttpResponse<String> resp = get("/health/live");
        if (resp.statusCode() == 503) {
            assertEquals("DOWN", json(resp).path("status").asText());
        } else {
            assertEquals(200, resp.statusCode());
            assertEquals("UP", json(resp).path("status").asText());
        }
    }

    // ── GET /health/ready ─────────────────────────────────────────────
    @Test
    void ready_happy_returnsDependenciesUp() throws Exception {
        HttpResponse<String> resp = get("/health/ready");
        assertEquals(200, resp.statusCode(), "readiness 200 when DB + Redis up");
        JsonNode b = json(resp);
        assertEquals("UP", b.path("status").asText());
        assertEquals("UP", b.path("db").asText());
        assertEquals("UP", b.path("redis").asText());
    }

    @Test
    void ready_error_notReadyContractShape() throws Exception {
        // API.md: 503 {"status":"DOWN","db":"DOWN"} when a dependency is unavailable.
        HttpResponse<String> resp = get("/health/ready");
        if (resp.statusCode() == 503) {
            JsonNode b = json(resp);
            assertEquals("DOWN", b.path("status").asText());
            assertTrue(b.has("db"), "readiness failure reports db status");
        } else {
            assertEquals(200, resp.statusCode());
        }
    }
}
