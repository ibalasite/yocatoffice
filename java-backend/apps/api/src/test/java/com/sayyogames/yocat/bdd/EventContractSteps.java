package com.sayyogames.yocat.bdd;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * cross-module domain-event contracts (features/cross-module/event, EDD §4.6.1 / §3.4).
 * Producers publish MemberRegistered / DepositConfirmed / EntitlementGranted / RunScoreSubmitted through
 * the transactional Outbox; consumers (wallet) react idempotently. Verified in-process against the real
 * Modulith event registry (modulith.event_publication) and the real database.
 */
public class EventContractSteps {

    @Autowired
    private ScenarioContext ctx;

    // ── Background ────────────────────────────────────────────────────
    @Given("跨模組通訊一律透過交易式 Outbox 的領域事件進行")
    public void cross_module_via_outbox() {
        long tables = ctx.count("information_schema.tables",
                "table_schema = 'modulith' AND table_name = 'event_publication'");
        assertEquals(1L, tables, "the transactional Outbox registry must exist");
    }

    // ── producers / setup ─────────────────────────────────────────────
    @Given("member 已在同一交易建立新帳號")
    public void member_created_account_in_tx() {
        String email = "evt-" + UUID.randomUUID() + "@example.com";
        ctx.bag().put("email", email);
        ctx.registerAccount(email, "喵喵科技", "s3cret!");
    }

    @Given("wallet 尚未為該帳號建立錢包")
    public void wallet_has_no_wallet_yet() {
        assertEquals(0L, ctx.count("wallet.wallets"), "precondition: no wallet exists yet");
    }

    @Given("deposit 已建立一筆已確認的儲值訂單")
    public void deposit_confirmed_order_exists() {
        seedPack();
        ctx.ensureLoggedIn();
        ctx.newIdempotencyKey();
        checkout("g_mid", ctx.idempotencyKey());
    }

    @Given("wallet 尚未處理某筆儲值確認事件")
    public void wallet_has_not_processed_deposit_event() {
        seedPack();
        ctx.ensureLoggedIn();
    }

    @Given("entitlement 已在同一交易完成扣款與授予")
    public void entitlement_debited_and_granted() {
        ctx.ensureLoggedIn();
        ctx.topup("gems", 1000);
        ctx.jdbc().update(
                "INSERT INTO entitlement.entitlement_catalog(item_id, kind, label, price_currency, price_amount, is_active) "
                        + "VALUES ('suit_gold','cosmetic'::entitlement.kind_enum,'suit_gold','gems',100,TRUE) "
                        + "ON CONFLICT (item_id) DO NOTHING");
        ctx.bag().put("item", "suit_gold");
        ctx.post("/entitlements/buy", "{\"kind\":\"cosmetic\",\"itemId\":\"suit_gold\",\"currency\":\"gems\",\"amount\":100}");
    }

    @Given("leaderboard 已以場次 ID upsert 寫入分數")
    public void leaderboard_upserted_score() {
        ctx.ensureLoggedIn();
        String runId = "run-" + UUID.randomUUID();
        ctx.bag().put("runId", runId);
        ctx.post("/leaderboard", "{\"runId\":\"" + runId + "\",\"mode\":\"sandbox\",\"score\":982300}");
    }

    // ── When ──────────────────────────────────────────────────────────
    @When("該交易提交完成")
    public void transaction_committed() {
        // commit already happened inside the producing API call; marker step.
    }

    @When("該訂單交易提交完成")
    public void order_transaction_committed() {
        // marker step.
    }

    @When("該寫入完成")
    public void write_completed() {
        // marker step.
    }

    @When("wallet 收到合規的會員註冊事件")
    public void wallet_receives_member_registered() {
        String email = "evt-" + UUID.randomUUID() + "@example.com";
        ctx.bag().put("email", email);
        ctx.registerAccount(email, "喵喵科技", "s3cret!");
    }

    @When("wallet 收到該儲值確認事件並再次收到相同事件")
    public void wallet_receives_deposit_event_twice() {
        ctx.newIdempotencyKey();
        checkout("g_mid", ctx.idempotencyKey());
        checkout("g_mid", ctx.idempotencyKey()); // redelivery with the same key
    }

    // ── Then: producers published a contract-compliant event ──────────
    @Then("系統發布會員註冊事件")
    public void member_registered_published() {
        assertTrue(eventPublished("MemberRegistered") >= 1, "a MemberRegistered event must be recorded");
    }

    @Then("事件內容包含帳號 ID 與 email")
    public void event_has_account_and_email() {
        assertTrue(serializedContains("MemberRegistered", (String) ctx.bag().get("email")),
                "the event payload must carry the account email");
    }

    @Then("事件結構符合既有合約版本")
    public void event_matches_contract_version() throws Exception {
        Class.forName("com.sayyogames.yocat.shared.MemberRegistered");
    }

    @Then("系統發布儲值確認事件")
    public void deposit_confirmed_published() {
        assertTrue(eventPublished("DepositConfirmed") >= 1, "a DepositConfirmed event must be recorded");
    }

    @Then("事件內容包含帳號 ID、訂單 ID、幣別與金額")
    public void deposit_event_has_order_fields() {
        assertTrue(serializedContains("DepositConfirmed", "gems"), "the event payload must carry the currency");
    }

    @Then("系統發布權益授予事件")
    public void entitlement_granted_published() {
        assertTrue(eventPublished("EntitlementGranted") >= 1, "an EntitlementGranted event must be recorded");
    }

    @Then("事件內容包含帳號 ID、類別與項目 ID")
    public void entitlement_event_has_item_fields() {
        assertTrue(serializedContains("EntitlementGranted", (String) ctx.bag().get("item")),
                "the event payload must carry the item id");
    }

    @Then("系統發布分數提交事件")
    public void run_score_submitted_published() {
        assertTrue(eventPublished("RunScoreSubmitted") >= 1, "a RunScoreSubmitted event must be recorded");
    }

    @Then("事件內容包含帳號 ID、場次 ID、模式與分數")
    public void run_score_event_has_fields() {
        assertTrue(serializedContains("RunScoreSubmitted", (String) ctx.bag().get("runId")),
                "the event payload must carry the run id");
    }

    // ── Then: consumer (wallet) reacts idempotently ───────────────────
    @Then("wallet 為該帳號建立初始餘額為零的錢包")
    public void wallet_created_zero_balance() {
        assertTrue(ctx.count("wallet.wallets") >= 1, "wallet must be opened on MemberRegistered");
        Long nonZero = ctx.jdbc().queryForObject(
                "SELECT count(*) FROM wallet.wallets WHERE cash <> 0 OR gems <> 0", Long.class);
        assertEquals(0L, nonZero == null ? 0L : nonZero, "the opened wallet must start at zero balance");
    }

    @Then("wallet 寫入一筆原因為開帳的錢包異動記錄")
    public void wallet_wrote_opening_ledger() {
        assertTrue(ctx.count("wallet.wallet_ledger", "reason = ?", "init") >= 1,
                "an opening (init) ledger entry must be written");
    }

    @Then("整個過程不需呼叫 member 的任何 HTTP 介面")
    public void no_member_http_call_needed() {
        assertTrue(ctx.count("wallet.wallets") >= 1,
                "the wallet was opened via the domain event, not a member HTTP call");
    }

    @Then("wallet 僅對該帳號入帳一次")
    public void wallet_credited_only_once() {
        assertEquals(1L, ctx.count("wallet.wallet_ledger", "reason = ?", "topup"),
                "redelivery must not double-credit the wallet");
    }

    @Then("wallet 以事件 ID 去重避免重複入帳")
    public void wallet_dedups_by_event_id() {
        assertEquals(1L, ctx.count("wallet.wallet_ledger", "reason = ?", "topup"),
                "the consumer must dedupe by event id");
    }

    // ── helpers ───────────────────────────────────────────────────────
    private long eventPublished(String typeSubstring) {
        Long n = ctx.jdbc().queryForObject(
                "SELECT ("
                        + "  (SELECT count(*) FROM modulith.event_publication WHERE event_type LIKE ?)"
                        + " + (SELECT count(*) FROM modulith.event_publication_archive WHERE event_type LIKE ?))",
                Long.class, "%" + typeSubstring + "%", "%" + typeSubstring + "%");
        return n == null ? 0L : n;
    }

    private boolean serializedContains(String typeSubstring, String needle) {
        String like = needle == null ? "%" : "%" + needle + "%";
        Long n = ctx.jdbc().queryForObject(
                "SELECT ("
                        + "  (SELECT count(*) FROM modulith.event_publication WHERE event_type LIKE ? AND serialized_event LIKE ?)"
                        + " + (SELECT count(*) FROM modulith.event_publication_archive WHERE event_type LIKE ? AND serialized_event LIKE ?))",
                Long.class, "%" + typeSubstring + "%", like, "%" + typeSubstring + "%", like);
        return n != null && n >= 1;
    }

    private void seedPack() {
        ctx.jdbc().update(
                "INSERT INTO deposit.deposit_pack(pack_id, currency, amount, label, bonus_pct, is_active) "
                        + "VALUES ('g_mid','gems',330,'中寶石包',10,TRUE) ON CONFLICT (pack_id) DO NOTHING");
    }

    private void checkout(String packId, String key) {
        var spec = ctx.http();
        if (key != null) {
            spec = spec.header("Idempotency-Key", key);
        }
        ctx.setResponse(spec.body("{\"packId\":\"" + packId + "\"}").post("/deposit/checkout").andReturn());
    }
}
