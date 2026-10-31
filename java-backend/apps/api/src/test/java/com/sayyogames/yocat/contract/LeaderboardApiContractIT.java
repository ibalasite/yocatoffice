package com.sayyogames.yocat.contract;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * API contract: leaderboard BC endpoints (API.md §3 BC leaderboard / openapi.yaml).
 * Endpoints: GET /leaderboard (public), POST /leaderboard (auth).
 */
class LeaderboardApiContractIT extends ApiContractSupport {

    // ── GET /leaderboard ──────────────────────────────────────────────
    @Test
    void list_happy_returnsLeaderboardArray() throws Exception {
        HttpResponse<String> resp = get("/leaderboard?mode=sandbox&limit=20"); // public endpoint
        assertEquals(200, resp.statusCode());
        JsonNode lb = json(resp).path("leaderboard");
        assertTrue(lb.isArray(), "leaderboard is an array (possibly empty)");
    }

    @Test
    void list_happy_entryShapeWhenPresent() throws Exception {
        HttpResponse<String> resp = get("/leaderboard?limit=1");
        assertEquals(200, resp.statusCode());
        JsonNode lb = json(resp).path("leaderboard");
        assertTrue(lb.isArray());
        if (lb.size() > 0) {
            JsonNode e = lb.get(0);
            assertTrue(e.hasNonNull("runId"));
            assertTrue(e.hasNonNull("name"));
            assertTrue(e.hasNonNull("mode"));
            assertTrue(e.path("score").isNumber());
            assertTrue(e.hasNonNull("updatedAt"));
        }
    }

    @Test
    void list_error_rateLimitedContractShape() throws Exception {
        // API.md: only documented error for the public GET is 429 RATE_LIMITED.
        HttpResponse<String> resp = get("/leaderboard");
        if (resp.statusCode() == 429) {
            assertErrorShape(resp, 429);
        } else {
            assertEquals(200, resp.statusCode());
        }
    }

    // ── POST /leaderboard ─────────────────────────────────────────────
    @Test
    void submit_happy_returnsOkFlag() throws Exception {
        HttpResponse<String> resp = postJsonAuth("/leaderboard",
                "{\"runId\":\"run-7781\",\"mode\":\"sandbox\",\"score\":982300,\"grade\":\"S\","
                        + "\"detail\":{\"days\":120,\"peakWorkers\":8}}");
        assertEquals(200, resp.statusCode());
        assertTrue(json(resp).path("ok").asBoolean());
    }

    @Test
    void submit_error_blankRunIdReturns400() throws Exception {
        HttpResponse<String> resp = postJsonAuth("/leaderboard", "{\"runId\":\"\",\"score\":10}");
        assertErrorShape(resp, 400); // VALIDATION_ERROR: 缺 runId
    }

    @Test
    void submit_error_missingTokenReturns401() throws Exception {
        HttpResponse<String> resp = postJson("/leaderboard", "{\"runId\":\"run-1\",\"score\":10}");
        assertErrorShape(resp, 401); // UNAUTHORIZED
    }
}
