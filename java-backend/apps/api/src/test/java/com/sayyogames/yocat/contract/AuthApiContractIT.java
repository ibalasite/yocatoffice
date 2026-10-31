package com.sayyogames.yocat.contract;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * API contract: member BC endpoints (API.md §3 BC member / openapi.yaml).
 * Endpoints: POST /auth/register, POST /auth/login, POST /auth/logout, GET /auth/me.
 */
class AuthApiContractIT extends ApiContractSupport {

    // ── POST /auth/register ───────────────────────────────────────────
    @Test
    void register_happy_returnsTokenAccountWallet() throws Exception {
        HttpResponse<String> resp = postJson("/auth/register",
                "{\"email\":\"boss@yocat.example\",\"displayName\":\"喵喵科技\",\"password\":\"s3cret!\"}");
        assertEquals(200, resp.statusCode());
        JsonNode b = json(resp);
        assertTrue(b.hasNonNull("token"), "AuthResponse.token present");
        assertTrue(b.path("account").hasNonNull("id"), "account.id present");
        assertTrue(b.path("account").hasNonNull("email"));
        assertTrue(b.path("account").hasNonNull("display_name"), "snake_case display_name per API.md");
        assertEquals(0L, b.path("wallet").path("cash").asLong(), "init wallet cash=0");
        assertEquals(0L, b.path("wallet").path("gems").asLong(), "init wallet gems=0");
    }

    @Test
    void register_error_duplicateEmailReturns409() throws Exception {
        HttpResponse<String> resp = postJson("/auth/register",
                "{\"email\":\"boss@yocat.example\",\"displayName\":\"喵喵科技\",\"password\":\"s3cret!\"}");
        assertErrorShape(resp, 409); // CONFLICT: email already registered
    }

    @Test
    void register_error_invalidEmailReturns400() throws Exception {
        HttpResponse<String> resp = postJson("/auth/register",
                "{\"email\":\"not-an-email\",\"displayName\":\"喵喵科技\",\"password\":\"s3cret!\"}");
        assertErrorShape(resp, 400); // VALIDATION_ERROR
    }

    // ── POST /auth/login ──────────────────────────────────────────────
    @Test
    void login_happy_returnsAuthResponse() throws Exception {
        HttpResponse<String> resp = postJson("/auth/login",
                "{\"email\":\"boss@yocat.example\",\"password\":\"s3cret!\"}");
        assertEquals(200, resp.statusCode());
        JsonNode b = json(resp);
        assertTrue(b.hasNonNull("token"));
        assertTrue(b.path("account").hasNonNull("id"));
        assertTrue(b.path("wallet").has("cash"));
        assertTrue(b.path("wallet").has("gems"));
    }

    @Test
    void login_error_wrongCredentialsReturns401() throws Exception {
        HttpResponse<String> resp = postJson("/auth/login",
                "{\"email\":\"boss@yocat.example\",\"password\":\"wrong-password\"}");
        assertErrorShape(resp, 401); // UNAUTHORIZED (no account enumeration)
    }

    // ── POST /auth/logout ─────────────────────────────────────────────
    @Test
    void logout_happy_returnsOkFlag() throws Exception {
        HttpResponse<String> resp = postJsonAuth("/auth/logout", "");
        assertEquals(200, resp.statusCode());
        assertTrue(json(resp).path("ok").asBoolean(), "logout ok:true");
    }

    @Test
    void logout_idempotent_withoutOrInvalidTokenStillReturns200() throws Exception {
        // API.md: logout is deliberately idempotent — returns 200 even without a valid token.
        HttpResponse<String> resp = postJson("/auth/logout", "");
        assertEquals(200, resp.statusCode(), "logout is idempotent: never 401");
        assertTrue(json(resp).path("ok").asBoolean());
    }

    // ── GET /auth/me ──────────────────────────────────────────────────
    @Test
    void me_happy_returnsAccountWalletEntitlements() throws Exception {
        HttpResponse<String> resp = getAuth("/auth/me");
        assertEquals(200, resp.statusCode());
        JsonNode b = json(resp);
        assertTrue(b.path("account").hasNonNull("id"));
        assertTrue(b.path("wallet").has("cash"));
        assertTrue(b.path("entitlements").isArray(), "entitlements is an array (possibly empty)");
    }

    @Test
    void me_error_missingTokenReturns401() throws Exception {
        HttpResponse<String> resp = get("/auth/me");
        assertErrorShape(resp, 401); // UNAUTHORIZED
    }
}
