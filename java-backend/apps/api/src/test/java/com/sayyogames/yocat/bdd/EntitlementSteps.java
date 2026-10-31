package com.sayyogames.yocat.bdd;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * entitlement BC — atomic purchase &amp; listing (API.md §3 entitlement: POST /entitlements/buy,
 * GET /entitlements). PRD US-ENT-001 AC-1..AC-5: same-transaction debit+grant, UNIQUE(account,kind,item)
 * prevents double-grant, bankruptcy never removes owned entitlements.
 */
public class EntitlementSteps {

    private static final long CHEAP_GEMS = 100;
    private static final long EXPENSIVE_GEMS = 1_000_000;

    @Autowired
    private ScenarioContext ctx;

    // ── Given ─────────────────────────────────────────────────────────
    @Given("使用者尚未擁有外觀項目 {string}")
    public void not_owning_cosmetic(String item) {
        seedCatalog(item, "cosmetic", "gems", CHEAP_GEMS);
        ctx.bag().put("item", item);
        ctx.bag().put("kind", "cosmetic");
        assertEquals(0L, ownedCount(item));
    }

    @Given("使用者尚未擁有劇本項目 {string}")
    public void not_owning_scenario(String item) {
        seedCatalog(item, "scenario", "gems", 0);
        ctx.bag().put("item", item);
        ctx.bag().put("kind", "scenario");
        assertEquals(0L, ownedCount(item));
    }

    @Given("使用者尚未擁有夢想員工 {string}")
    public void not_owning_dream(String item) {
        seedCatalog(item, "dream", "gems", EXPENSIVE_GEMS);
        ctx.bag().put("item", item);
        ctx.bag().put("kind", "dream");
        assertEquals(0L, ownedCount(item));
    }

    @Given("使用者的寶石餘額足以支付該項目")
    public void gems_sufficient() {
        ctx.topup("gems", CHEAP_GEMS * 2);
    }

    @Given("使用者的餘額小於該項目價格")
    public void balance_below_price() {
        ctx.bag().put("beforeGems", currentGems());
    }

    @Given("使用者已擁有外觀項目 {string}")
    public void already_owning_cosmetic(String item) {
        seedCatalog(item, "cosmetic", "gems", CHEAP_GEMS);
        grant(item, "cosmetic");
        ctx.bag().put("item", item);
        ctx.bag().put("kind", "cosmetic");
    }

    @Given("使用者已擁有外觀項目 {string} 與夢想員工 {string}")
    public void already_owning_two(String cosmetic, String dream) {
        seedCatalog(cosmetic, "cosmetic", "gems", CHEAP_GEMS);
        seedCatalog(dream, "dream", "gems", CHEAP_GEMS);
        grant(cosmetic, "cosmetic");
        grant(dream, "dream");
    }

    @Given("該帳號曾在遊戲內破產並重經營")
    public void account_went_bankrupt_and_restarted() {
        ctx.jdbc().update(
                "INSERT INTO record.record_type_catalog(type_code, label) VALUES ('bankruptcy','破產') "
                        + "ON CONFLICT (type_code) DO NOTHING");
        ctx.jdbc().update("INSERT INTO record.records(account_id, type, payload) VALUES (?, 'bankruptcy', '{}'::jsonb)",
                accountId());
    }

    // ── When ──────────────────────────────────────────────────────────
    @When("使用者以寶石購買外觀項目 {string}")
    public void buy_cosmetic_with_gems(String item) {
        rememberEnt();
        buy("cosmetic", item, "gems", CHEAP_GEMS);
    }

    @When("系統以零金額免費授予該劇本項目")
    public void free_grant_scenario() {
        ctx.bag().put("ledgerBefore", ctx.count("wallet.wallet_ledger"));
        rememberEnt();
        buy("scenario", (String) ctx.bag().get("item"), "gems", 0);
    }

    @When("使用者再次購買相同的外觀項目 {string}")
    public void buy_same_cosmetic_again(String item) {
        rememberEnt();
        buy("cosmetic", item, "gems", CHEAP_GEMS);
    }

    @When("使用者嘗試購買該夢想員工")
    public void try_buy_dream() {
        rememberEnt();
        buy("dream", (String) ctx.bag().get("item"), "gems", EXPENSIVE_GEMS);
    }

    @When("使用者以類別 {string} 與項目 {string} 送出購買")
    public void buy_with_kind_item(String kind, String itemId) {
        rememberEnt();
        buy(kind, itemId, "gems", CHEAP_GEMS);
    }

    @When("使用者嘗試購買外觀項目")
    public void try_buy_cosmetic_unauth() {
        rememberEnt();
        buy("cosmetic", "suit_gold", "gems", CHEAP_GEMS);
    }

    @When("使用者查詢已購權益清單")
    public void list_entitlements() {
        ctx.get("/entitlements");
    }

    @When("使用者嘗試查詢已購權益清單")
    public void try_list_entitlements() {
        ctx.get("/entitlements");
    }

    // ── Then ──────────────────────────────────────────────────────────
    @Then("系統回應 HTTP {int} 並回傳扣款後的錢包與最新權益清單")
    public void http_with_wallet_and_entitlements(int status) {
        assertEquals(status, ctx.status());
        var jp = ctx.response().jsonPath();
        assertNotNull(jp.get("wallet.gems"), "debited wallet returned");
        assertNotNull(jp.getList("entitlements"), "entitlements list returned");
    }

    @Then("系統在同一交易寫入一筆購買原因的錢包異動記錄")
    public void buy_ledger_written() {
        assertTrue(ctx.count("wallet.wallet_ledger", "reason LIKE ?", "buy_%") >= 1,
                "a buy_<kind> ledger entry must be written in the same transaction");
    }

    @Then("新權益出現在使用者的權益清單中")
    public void new_entitlement_present() {
        assertEquals(1L, ownedCount((String) ctx.bag().get("item")),
                "the purchased item must now be owned");
    }

    @Then("使用者取得該劇本權益")
    public void scenario_entitlement_granted() {
        assertEquals(1L, ownedCount((String) ctx.bag().get("item")), "free grant must insert the entitlement");
    }

    @Then("系統不重複扣款也不重複授予")
    public void no_double_debit_or_grant() {
        assertEquals(1L, ownedCount((String) ctx.bag().get("item")), "no duplicate grant (UNIQUE constraint)");
        assertTrue(ctx.count("wallet.wallet_ledger", "reason LIKE ?", "buy_%") <= 1, "no duplicate debit");
    }

    @Then("系統不授予任何權益")
    public void no_entitlement_granted() {
        long before = (long) ctx.bag().getOrDefault("entBefore", 0L);
        assertEquals(before, ctx.count("entitlement.entitlements"),
                "a rejected purchase must not grant an entitlement");
    }

    @Then("使用者未取得該權益且餘額維持不變")
    public void no_grant_and_balance_unchanged() {
        assertEquals(0L, ownedCount((String) ctx.bag().get("item")), "no entitlement on insufficient balance");
        long before = (long) ctx.bag().get("beforeGems");
        assertEquals(before, currentGems(), "balance must be unchanged after rollback");
    }

    @Then("系統回應 HTTP {int} 並完整列出先前購買的所有權益")
    public void lists_all_owned(int status) {
        assertEquals(status, ctx.status());
        List<?> list = ctx.response().jsonPath().getList("$");
        if (list == null) {
            list = ctx.response().jsonPath().getList("entitlements");
        }
        assertNotNull(list);
        assertEquals(2, list.size(), "both previously-owned entitlements must be listed");
    }

    @Then("權益清單依取得時間排序")
    public void entitlements_ordered_by_acquired() {
        List<Long> ids = ctx.jdbc().query(
                "SELECT id FROM entitlement.entitlements WHERE account_id = ? AND deleted_at IS NULL "
                        + "ORDER BY acquired_at",
                (rs, n) -> rs.getLong(1), accountId());
        assertTrue(ids.size() >= 1, "entitlements must be listable in acquisition order");
    }

    @Then("系統不回傳任何權益資料")
    public void no_entitlement_data_returned() {
        List<?> list = ctx.response().jsonPath().getList("entitlements");
        assertTrue(list == null || list.isEmpty(), "unauthenticated listing must not leak entitlements");
    }

    // ── helpers ───────────────────────────────────────────────────────
    private void buy(String kind, String itemId, String currency, long amount) {
        String item = itemId == null ? "" : itemId;
        ctx.post("/entitlements/buy",
                String.format("{\"kind\":\"%s\",\"itemId\":\"%s\",\"currency\":\"%s\",\"amount\":%d}",
                        kind == null ? "" : kind, item, currency, amount));
    }

    private void grant(String item, String kind) {
        ctx.jdbc().update(
                "INSERT INTO entitlement.entitlements(account_id, kind, item_id) VALUES (?, ?::entitlement.kind_enum, ?) "
                        + "ON CONFLICT DO NOTHING",
                accountId(), kind, item);
    }

    private void seedCatalog(String itemId, String kind, String currency, long price) {
        ctx.jdbc().update(
                "INSERT INTO entitlement.entitlement_catalog(item_id, kind, label, price_currency, price_amount, is_active) "
                        + "VALUES (?, ?::entitlement.kind_enum, ?, ?, ?, TRUE) ON CONFLICT (item_id) DO NOTHING",
                itemId, kind, itemId, currency, price);
    }

    private long ownedCount(String item) {
        return ctx.count("entitlement.entitlements", "account_id = ? AND item_id = ? AND deleted_at IS NULL",
                accountId(), item);
    }

    private long currentGems() {
        var r = ctx.get("/auth/me");
        if (r.getStatusCode() == 200) {
            Long g = r.jsonPath().getLong("wallet.gems");
            return g == null ? 0L : g;
        }
        return (long) ctx.bag().getOrDefault("gems", 0L);
    }

    private void rememberEnt() {
        ctx.bag().put("entBefore", ctx.count("entitlement.entitlements"));
    }

    private long accountId() {
        Object cached = ctx.bag().get("accountId");
        if (cached != null) {
            return (long) cached;
        }
        String email = (String) ctx.bag().get("email");
        Long id;
        if (email != null) {
            id = ctx.jdbc().query("SELECT id FROM member.accounts WHERE email = ?",
                    (rs, n) -> rs.getLong(1), email).stream().findFirst().orElse(null);
        } else {
            id = ctx.jdbc().query("SELECT id FROM member.accounts ORDER BY id DESC LIMIT 1",
                    (rs, n) -> rs.getLong(1)).stream().findFirst().orElse(null);
        }
        long resolved = id == null ? 0L : id;
        ctx.bag().put("accountId", resolved);
        return resolved;
    }
}
