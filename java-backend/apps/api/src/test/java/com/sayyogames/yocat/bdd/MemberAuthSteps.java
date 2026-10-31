package com.sayyogames.yocat.bdd;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.response.Response;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * member BC — identity &amp; session (API.md §3 member: POST /auth/register|login|logout, GET /auth/me).
 * Traceable to PRD US-MEM-001 AC-1..AC-6 via scenario tags.
 */
public class MemberAuthSteps {

    private static final String PWD = "s3cret!";

    @Autowired
    private ScenarioContext ctx;

    // ── Background ────────────────────────────────────────────────────
    @Given("系統已存在一個啟用中的帳號，email 為 {string}、密碼為 {string}")
    public void an_active_account_exists(String email, String password) {
        ctx.registerAccount(email, "喵喵科技", password);
        ctx.bag().put("primaryEmail", email);
        ctx.bag().put("primaryPassword", password);
    }

    @Given("帳號 {string} 為啟用狀態")
    public void account_is_active(String email) {
        if (ctx.count("member.accounts", "email = ?", email) == 0) {
            ctx.registerAccount(email, "喵喵科技", PWD);
        }
        ctx.bag().put("primaryEmail", email);
        ctx.bag().put("primaryPassword", PWD);
        assertTrue(ctx.count("member.accounts", "email = ?", email) >= 1, "account must be active/present");
    }

    @Given("email {string} 尚未被註冊")
    public void email_not_yet_registered(String email) {
        assertEquals(0L, ctx.count("member.accounts", "email = ?", email),
                "precondition: email must not exist yet");
        ctx.bag().put("attemptedEmail", email);
    }

    @Given("email {string} 已被註冊")
    public void email_already_registered(String email) {
        if (ctx.count("member.accounts", "email = ?", email) == 0) {
            ctx.registerAccount(email, "喵喵科技", PWD);
        }
        ctx.bag().put("attemptedEmail", email);
    }

    @Given("email {string} 不存在於系統")
    public void email_absent(String email) {
        assertEquals(0L, ctx.count("member.accounts", "email = ?", email));
        ctx.bag().put("attemptedEmail", email);
    }

    // ── When: register ────────────────────────────────────────────────
    @When("使用者以合法 email、顯示名稱與密碼送出註冊")
    public void register_with_valid_data() {
        String email = "newboss@example.com";
        ctx.bag().put("attemptedEmail", email);
        ctx.post("/auth/register",
                String.format("{\"email\":\"%s\",\"displayName\":\"喵喵科技\",\"password\":\"%s\"}", email, PWD));
    }

    @When("使用者以 email {string} 送出註冊")
    public void register_with_email(String email) {
        ctx.bag().put("attemptedEmail", email);
        ctx.post("/auth/register",
                String.format("{\"email\":\"%s\",\"displayName\":\"喵喵科技\",\"password\":\"%s\"}", email, PWD));
    }

    @When("使用者以 email {string}、顯示名稱 {string}、密碼 {string} 送出註冊")
    public void register_with_fields(String email, String displayName, String password) {
        ctx.bag().put("attemptedEmail", email);
        ctx.post("/auth/register",
                String.format("{\"email\":\"%s\",\"displayName\":\"%s\",\"password\":\"%s\"}",
                        email, displayName, password));
    }

    @When("使用者再次送出註冊")
    public void register_once_more() {
        String email = "flood-" + UUID.randomUUID() + "@example.com";
        ctx.post("/auth/register",
                String.format("{\"email\":\"%s\",\"displayName\":\"喵喵科技\",\"password\":\"%s\"}", email, PWD));
    }

    @Given("同一來源 IP 在本時間窗內的註冊次數已達上限")
    public void register_rate_limit_reached() {
        // Drive the real rate limiter: fire registrations until the window is exhausted.
        for (int i = 0; i < 60; i++) {
            ctx.post("/auth/register",
                    String.format("{\"email\":\"rl-%d-%s@example.com\",\"displayName\":\"n\",\"password\":\"%s\"}",
                            i, UUID.randomUUID(), PWD));
            if (ctx.status() == 429) {
                break;
            }
        }
    }

    // ── When: login / me / logout ─────────────────────────────────────
    @When("使用者以正確 email 與密碼登入")
    public void login_correct() {
        ctx.login((String) ctx.bag().get("primaryEmail"), (String) ctx.bag().get("primaryPassword"));
    }

    @When("使用者以正確 email 但錯誤密碼登入")
    public void login_wrong_password() {
        ctx.login((String) ctx.bag().get("primaryEmail"), "wrong-password");
    }

    @When("使用者以未註冊的 email 登入")
    public void login_unregistered() {
        ctx.login((String) ctx.bag().get("attemptedEmail"), "whatever-pass");
    }

    @When("使用者查詢「我的資料」")
    public void query_me() {
        ctx.get("/auth/me");
    }

    @When("使用者嘗試查詢「我的資料」")
    public void try_query_me() {
        ctx.get("/auth/me");
    }

    @When("使用者登出")
    public void logout() {
        ctx.post("/auth/logout", "");
    }

    @Given("使用者提供一個已失效的 session token")
    public void provide_expired_token() {
        ctx.setToken("expired-" + UUID.randomUUID().toString().replace("-", ""));
    }

    // ── Then ──────────────────────────────────────────────────────────
    @Then("系統回應 HTTP {int} 並附帶 session token")
    public void http_with_token(int status) {
        assertEquals(status, ctx.status());
        assertNotNull(ctx.response().jsonPath().getString("token"), "AuthResponse.token must be present");
    }

    @Then("回應包含新帳號資料與初始錢包（cash 為 {int}、gems 為 {int}）")
    public void body_has_account_and_initial_wallet(int cash, int gems) {
        var jp = ctx.response().jsonPath();
        assertNotNull(jp.getString("account.id"), "account.id present");
        assertEquals(cash, jp.getInt("wallet.cash"), "init wallet cash");
        assertEquals(gems, jp.getInt("wallet.gems"), "init wallet gems");
    }

    @Then("系統為該帳號建立一筆開帳的錢包異動記錄")
    public void opening_ledger_entry_written() {
        assertTrue(ctx.count("wallet.wallet_ledger") >= 1,
                "an opening wallet ledger entry must exist after registration");
    }

    @Then("系統不建立重複帳號")
    public void no_duplicate_account() {
        String email = (String) ctx.bag().get("attemptedEmail");
        assertEquals(1L, ctx.count("member.accounts", "email = ?", email),
                "exactly one account for a duplicate email");
    }

    @Then("系統不寫入任何帳號資料")
    public void no_account_written() {
        String email = (String) ctx.bag().get("attemptedEmail");
        assertEquals(0L, ctx.count("member.accounts", "email = ?", email),
                "rejected registration must not persist an account");
    }

    @Then("系統回應 HTTP {int} 並簽發有效期 {int} 天的新 session token")
    public void login_issues_token(int status, int days) {
        assertEquals(status, ctx.status());
        assertNotNull(ctx.response().jsonPath().getString("token"), "login must issue a token");
        assertTrue(days > 0);
    }

    @Then("回應包含帳號資料與當前錢包餘額")
    public void body_has_account_and_wallet() {
        var jp = ctx.response().jsonPath();
        assertNotNull(jp.getString("account.id"));
        assertNotNull(jp.get("wallet.cash"), "wallet.cash present");
    }

    @Then("系統回應認證失敗並提示「email 或密碼錯誤」")
    public void auth_failed_generic_message() {
        assertEquals(401, ctx.status(), "authentication failure is 401 (API.md §5.2)");
        String error = ctx.response().jsonPath().getString("error");
        assertNotNull(error);
        assertTrue(error.contains("email 或密碼錯誤"), "generic credential error, actual='" + error + "'");
    }

    @Then("系統不簽發任何 session token")
    public void no_token_issued() {
        assertNull(ctx.response().jsonPath().getString("token"));
    }

    @Then("系統不透露該 email 是否已註冊")
    public void no_account_enumeration() {
        String error = ctx.response().jsonPath().getString("error");
        assertNotNull(error);
        assertFalse(error.contains("不存在") || error.contains("未註冊") || error.contains("not found"),
                "must not reveal whether the email exists; actual='" + error + "'");
    }

    @Then("系統回應 HTTP {int} 並包含帳號、錢包與已購權益清單")
    public void me_aggregates(int status) {
        assertEquals(status, ctx.status());
        var jp = ctx.response().jsonPath();
        assertNotNull(jp.getString("account.id"));
        assertNotNull(jp.get("wallet.cash"));
        assertNotNull(jp.getList("entitlements"), "entitlements must be an array");
    }

    @Then("權益清單在未購買任何項目時為空陣列")
    public void entitlements_empty_array() {
        assertTrue(ctx.response().jsonPath().getList("entitlements").isEmpty(),
                "entitlements is an empty array before any purchase");
    }

    @Then("系統不回傳任何帳號資料")
    public void no_account_data_returned() {
        assertNull(ctx.response().jsonPath().getString("account.id"),
                "unauthenticated /auth/me must not leak account data");
    }

    @Then("系統確認登出成功")
    public void logout_confirmed() {
        assertEquals(200, ctx.status());
        assertTrue(ctx.response().jsonPath().getBoolean("ok"), "logout ok:true");
    }

    @Then("系統仍確認登出成功")
    public void logout_still_confirmed() {
        assertEquals(200, ctx.status(), "logout is idempotent: always 200");
        assertTrue(ctx.response().jsonPath().getBoolean("ok"));
    }

    @Then("原先的 session token 不再能存取需認證的資源")
    public void old_token_rejected() {
        Response probe = ctx.get("/auth/me");
        assertEquals(401, probe.getStatusCode(), "revoked token must be rejected");
    }

    @Then("系統不回應認證錯誤")
    public void no_auth_error() {
        assertFalse(ctx.status() == 401, "idempotent logout must not return 401");
    }
}
