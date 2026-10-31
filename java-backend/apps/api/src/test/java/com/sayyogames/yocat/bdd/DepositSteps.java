package com.sayyogames.yocat.bdd;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.response.Response;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * deposit BC — catalog &amp; idempotent checkout (API.md §3 deposit: GET /deposit/packs,
 * POST /deposit/checkout + Idempotency-Key). PRD US-DEP-001 AC-1/AC-2: Phase-A fake-pay confirms
 * immediately and credits the wallet via the DepositConfirmed event; replay is idempotent.
 */
public class DepositSteps {

    @Autowired
    private ScenarioContext ctx;

    // ── Background / Given ────────────────────────────────────────────
    @Given("儲值目錄已載入，包含寶石包與資本包")
    public void deposit_catalog_loaded() {
        seedPack("g_small", "gems", 60, "小寶石包", null);
        seedPack("g_mid", "gems", 330, "中寶石包", 10);
        seedPack("g_big", "gems", 700, "大寶石包", 16);
        seedPack("cap_1000", "cash", 1000, "資本儲值包", null);
    }

    @Given("使用者提供一個全新的冪等鍵")
    public void a_fresh_idempotency_key() {
        ctx.newIdempotencyKey();
    }

    @Given("使用者已以某冪等鍵建立過一筆已確認的儲值訂單")
    public void a_confirmed_order_exists_with_key() {
        ctx.ensureLoggedIn();
        ctx.newIdempotencyKey();
        checkout("g_mid", ctx.idempotencyKey());
        ctx.bag().put("firstOrderId", ctx.response().jsonPath().getString("orderId"));
        ctx.bag().put("firstPack", "g_mid");
    }

    @Given("使用者已以某冪等鍵建立過一筆儲值訂單")
    public void an_order_exists_with_key() {
        a_confirmed_order_exists_with_key();
    }

    // ── When ──────────────────────────────────────────────────────────
    @When("使用者查詢儲值包目錄")
    public void query_packs() {
        ctx.get("/deposit/packs");
    }

    @When("使用者以大寶石包建立儲值訂單")
    public void checkout_big_pack() {
        if (ctx.idempotencyKey() == null) {
            ctx.newIdempotencyKey();
        }
        ctx.bag().put("orderBefore", ctx.count("deposit.deposit_order"));
        checkout("g_big", ctx.idempotencyKey());
    }

    @When("使用者以相同的冪等鍵再次送出相同的儲值請求")
    public void replay_same_key_same_pack() {
        checkout((String) ctx.bag().get("firstPack"), ctx.idempotencyKey());
    }

    @When("使用者以相同冪等鍵但不同的儲值包送出請求")
    public void replay_same_key_different_pack() {
        ctx.bag().put("orderBefore", ctx.count("deposit.deposit_order"));
        checkout("g_small", ctx.idempotencyKey());
    }

    @When("使用者以不在目錄中的儲值包建立訂單")
    public void checkout_unknown_pack() {
        ctx.bag().put("orderBefore", ctx.count("deposit.deposit_order"));
        checkout("not_a_real_pack", ctx.idempotencyKey());
    }

    @When("使用者在未提供冪等鍵的情況下建立儲值訂單")
    public void checkout_without_key() {
        ctx.bag().put("orderBefore", ctx.count("deposit.deposit_order"));
        ctx.post("/deposit/checkout", "{\"packId\":\"g_mid\"}"); // no Idempotency-Key header
    }

    @When("使用者嘗試建立儲值訂單")
    public void try_checkout_unauthenticated() {
        ctx.bag().put("orderBefore", ctx.count("deposit.deposit_order"));
        checkout("g_mid", ctx.idempotencyKey() == null ? ctx.newIdempotencyKey() : ctx.idempotencyKey());
    }

    // ── Then ──────────────────────────────────────────────────────────
    @Then("系統回應 HTTP {int} 並回傳所有可購買的儲值包")
    public void packs_listed(int status) {
        assertEquals(status, ctx.status());
        List<?> packs = ctx.response().jsonPath().getList("$");
        assertNotNull(packs);
        assertFalse(packs.isEmpty(), "catalog must list the purchasable packs");
    }

    @Then("每個儲值包包含包 ID、幣別、入帳數量與顯示名稱")
    public void pack_fields_present() {
        List<Map<String, Object>> packs = ctx.response().jsonPath().getList("$");
        for (Map<String, Object> p : packs) {
            assertTrue(p.containsKey("packId") || p.containsKey("pack_id"), "packId present");
            assertTrue(p.containsKey("currency"), "currency present");
            assertTrue(p.containsKey("amount"), "amount present");
            assertTrue(p.containsKey("label"), "label present");
        }
    }

    @Then("系統回應 HTTP {int} 並回傳狀態為已確認的訂單")
    public void order_confirmed(int status) {
        assertEquals(status, ctx.status());
        String s = ctx.response().jsonPath().getString("status");
        assertNotNull(s, "order status present");
        assertTrue(s.toLowerCase().contains("confirm") || s.contains("已確認"),
                "Phase-A order must be confirmed, was '" + s + "'");
    }

    @Then("回應包含訂單 ID、包 ID、幣別與入帳數量")
    public void order_fields_present() {
        var jp = ctx.response().jsonPath();
        assertNotNull(jp.getString("orderId"), "orderId present");
        assertNotNull(jp.getString("packId"), "packId present");
        assertNotNull(jp.getString("currency"), "currency present");
        assertNotNull(jp.get("amount"), "amount present");
    }

    @Then("系統回放先前那筆訂單的結果")
    public void replays_previous_order() {
        assertEquals(ctx.bag().get("firstOrderId"), ctx.response().jsonPath().getString("orderId"),
                "same idempotency key must replay the original order");
    }

    @Then("使用者的錢包僅入帳一次")
    public void wallet_credited_once() {
        assertEquals(1L, ctx.count("wallet.wallet_ledger", "reason = ?", "topup"),
                "idempotent deposit credits the wallet exactly once");
    }

    @Then("系統拒絕請求並要求提供冪等鍵")
    public void rejects_requiring_idempotency_key() {
        assertEquals(400, ctx.status(), "a missing Idempotency-Key is rejected with 400 (API.md §2.6)");
        String error = ctx.response().jsonPath().getString("error");
        assertNotNull(error);
        assertTrue(error.contains("冪等") || error.toLowerCase().contains("idempotency"),
                "error must ask for the idempotency key, actual='" + error + "'");
    }

    @Then("系統不建立新的訂單")
    public void no_new_order() {
        long before = (long) ctx.bag().getOrDefault("orderBefore", 1L);
        assertEquals(before, ctx.count("deposit.deposit_order"), "conflict must not create a new order");
    }

    @Then("系統不建立任何訂單")
    public void no_order_created() {
        long before = (long) ctx.bag().getOrDefault("orderBefore", 0L);
        assertEquals(before, ctx.count("deposit.deposit_order"), "rejected checkout must not create an order");
    }

    // ── helpers ───────────────────────────────────────────────────────
    private Response checkout(String packId, String key) {
        var spec = ctx.http();
        if (key != null) {
            spec = spec.header("Idempotency-Key", key);
        }
        Response r = spec.body("{\"packId\":\"" + packId + "\"}").post("/deposit/checkout").andReturn();
        ctx.setResponse(r);
        return r;
    }

    private void seedPack(String id, String currency, long amount, String label, Integer bonus) {
        ctx.jdbc().update(
                "INSERT INTO deposit.deposit_pack(pack_id, currency, amount, label, bonus_pct, is_active) "
                        + "VALUES (?,?,?,?,?,TRUE) ON CONFLICT (pack_id) DO NOTHING",
                id, currency, amount, label, bonus);
    }
}
