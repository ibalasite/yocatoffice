package com.sayyogames.yocat.bdd;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.restassured.response.Response;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Step definitions shared across every bounded context: clean-DB background, session presence/absence,
 * and the generic documented-error assertions (API.md §5.2 — every error body carries {@code error}).
 * Centralised here so each phrase has exactly one definition (no duplicate / no undefined steps).
 */
public class CommonSteps {

    @Autowired
    private ScenarioContext ctx;

    // ── Background / preconditions ────────────────────────────────────
    @Given("資料庫已初始化為乾淨狀態")
    public void database_is_clean() {
        // Hooks truncates before each scenario; make the precondition explicit & verifiable.
        assertEquals(0L, ctx.count("member.accounts"), "clean DB: no residual accounts");
    }

    @Given("使用者持有有效的 session token")
    public void user_has_valid_session_token() {
        ctx.ensureLoggedIn();
        assertNotNull(ctx.token(), "a real session token must have been issued by /auth/register|login");
    }

    @Given("使用者未提供有效的 session token")
    public void user_has_no_valid_session_token() {
        ctx.clearToken();
    }

    @Given("使用者未提供 session token")
    public void user_provides_no_session_token() {
        ctx.clearToken();
    }

    @Given("已存在一個登入中的帳號")
    public void a_logged_in_account_exists() {
        ctx.ensureLoggedIn();
        assertNotNull(ctx.token(), "logged-in account requires a real token");
    }

    @Given("已存在登入中的帳號，其錢包餘額為 cash {int}、gems {int}")
    public void a_logged_in_account_with_balances(int cash, int gems) {
        ctx.ensureLoggedIn();
        if (cash > 0) {
            ctx.topup("cash", cash);
        }
        if (gems > 0) {
            ctx.topup("gems", gems);
        }
        ctx.bag().put("cash", cash);
        ctx.bag().put("gems", gems);
    }

    // ── generic documented error assertions ───────────────────────────
    /** e.g. 「系統回應 HTTP 409 並提示「這個 email 已經註冊過了」」 — exact status + error message. */
    @Then("^系統回應 HTTP (\\d+) 並提示「(.+)」$")
    public void system_responds_http_with_message(int status, String message) {
        Response r = ctx.response();
        assertNotNull(r, "a When step must have fired a request");
        assertEquals(status, r.getStatusCode(), "documented HTTP status (API.md §5.2)");
        String error = r.jsonPath().getString("error");
        assertNotNull(error, "API.md §5.2: error body must carry an 'error' field");
        assertTrue(error.contains(core(message)),
                "error should surface the documented message: expected≈'" + message + "' actual='" + error + "'");
    }

    /** e.g. 「系統拒絕購買並提示「項目不正確」」 — some 4xx with the documented message (status left to spec). */
    @Then("^系統拒絕[^「]*並提示「(.+)」$")
    public void system_rejects_with_message(String message) {
        Response r = ctx.response();
        assertNotNull(r, "a When step must have fired a request");
        assertTrue(r.getStatusCode() >= 400 && r.getStatusCode() < 500,
                "rejection must be a 4xx, was " + r.getStatusCode());
        String error = r.jsonPath().getString("error");
        assertNotNull(error, "API.md §5.2: error body must carry an 'error' field");
        assertTrue(error.contains(core(message)),
                "error should surface '" + message + "', actual='" + error + "'");
    }

    @Then("^系統拒絕註冊並回應「(.+)」$")
    public void system_rejects_registration_with_message(String message) {
        Response r = ctx.response();
        assertEquals(400, r.getStatusCode(), "fail-fast validation returns 400 (API.md §2.7)");
        String error = r.jsonPath().getString("error");
        assertNotNull(error);
        assertTrue(error.contains(core(message)),
                "validation error should be '" + message + "', actual='" + error + "'");
    }

    @Then("系統回應 HTTP {int} 並提示冪等鍵衝突")
    public void system_responds_idempotency_conflict(int status) {
        Response r = ctx.response();
        assertEquals(status, r.getStatusCode());
        assertNotNull(r.jsonPath().getString("error"), "409 body must carry an 'error' field");
    }

    @Then("回應附帶建議的重試等待時間")
    public void response_carries_retry_after() {
        Response r = ctx.response();
        assertNotNull(r.getHeader("Retry-After"), "429/503 must carry a Retry-After header (API.md §6)");
    }

    /** Trims the leading field label some feature messages prefix (none today) and normalises spacing. */
    private static String core(String message) {
        return message.trim();
    }
}
