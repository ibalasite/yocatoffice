package com.sayyogames.yocat.bdd;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.response.Response;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * wallet BC — server-authoritative wallet (API.md §3 wallet: POST /wallet/topup, POST /wallet/spend).
 * PRD US-WAL-001 AC-1..AC-5: append-only ledger, row-locked spend, 402 on insufficient balance,
 * amount bounds, concurrency consistency.
 */
public class WalletSteps {

    @Autowired
    private ScenarioContext ctx;

    // ── Given ─────────────────────────────────────────────────────────
    @Given("使用者的現金餘額不少於欲扣款金額")
    public void cash_at_least_spend() {
        ctx.topup("cash", 50_000);
        ctx.bag().put("spend", 1_250L);
    }

    @Given("使用者的現金餘額小於欲扣款金額")
    public void cash_less_than_spend() {
        long current = currentCash();
        ctx.bag().put("spend", current + 100_000L);
    }

    @Given("使用者的現金餘額為固定的已知金額")
    public void cash_fixed_known_amount() {
        ctx.bag().put("initialCash", currentCash());
    }

    @Given("該帳號在本時間窗內的錢包請求次數已達上限")
    public void wallet_rate_limit_reached() {
        for (int i = 0; i < 60; i++) {
            ctx.post("/wallet/spend", "{\"currency\":\"cash\",\"amount\":1,\"reason\":\"spend\"}");
            if (ctx.status() == 429) {
                break;
            }
        }
    }

    // ── When ──────────────────────────────────────────────────────────
    @When("使用者以中寶石包儲值")
    public void topup_with_mid_gem_pack() {
        rememberLedger();
        ctx.post("/wallet/topup", "{\"pack\":\"g_mid\"}");
    }

    @When("使用者以現金自訂金額 {int} 儲值")
    public void topup_custom_cash(int amount) {
        ctx.bag().put("beforeCash", currentCash());
        ctx.bag().put("topupAmount", (long) amount);
        rememberLedger();
        ctx.post("/wallet/topup", "{\"currency\":\"cash\",\"amount\":" + amount + "}");
    }

    @When("使用者以現金花費 {int}")
    public void spend_cash(int amount) {
        ctx.bag().put("beforeCash", currentCash());
        rememberLedger();
        ctx.post("/wallet/spend", "{\"currency\":\"cash\",\"amount\":" + amount + ",\"reason\":\"spend\"}");
    }

    @When("使用者嘗試以現金花費超過餘額的金額")
    public void spend_more_than_balance() {
        long spend = (long) ctx.bag().getOrDefault("spend", currentCash() + 100_000L);
        rememberLedger();
        ctx.post("/wallet/spend", "{\"currency\":\"cash\",\"amount\":" + spend + ",\"reason\":\"spend\"}");
    }

    @When("使用者嘗試花費")
    public void try_spend_unauthenticated() {
        rememberLedger();
        ctx.post("/wallet/spend", "{\"currency\":\"cash\",\"amount\":100,\"reason\":\"spend\"}");
    }

    @When("使用者以 {string} 操作提交金額 {int}")
    public void submit_amount_for_operation(String operation, int amount) {
        rememberLedger();
        ctx.bag().put("beforeCash", currentCash());
        if ("花費".equals(operation)) {
            ctx.post("/wallet/spend", "{\"currency\":\"cash\",\"amount\":" + amount + ",\"reason\":\"spend\"}");
        } else {
            ctx.post("/wallet/topup", "{\"currency\":\"cash\",\"amount\":" + amount + "}");
        }
    }

    @When("使用者再次送出花費請求")
    public void spend_once_more() {
        ctx.post("/wallet/spend", "{\"currency\":\"cash\",\"amount\":1,\"reason\":\"spend\"}");
    }

    @When("同一帳號同時送出多筆現金花費請求")
    public void concurrent_spends() throws InterruptedException {
        long initial = currentCash();
        ctx.bag().put("initialCash", initial);
        int threads = 10;
        long each = Math.max(1, initial / 8); // deliberately oversubscribe so some must fail
        String token = ctx.token();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        List<Long> succeeded = new CopyOnWriteArrayList<>();
        List<Thread> pool = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Thread t = new Thread(() -> {
                try {
                    start.await();
                    Response r = io.restassured.RestAssured.given()
                            .contentType(io.restassured.http.ContentType.JSON)
                            .header("Authorization", "Bearer " + token)
                            .body("{\"currency\":\"cash\",\"amount\":" + each + ",\"reason\":\"spend\"}")
                            .post("/wallet/spend").andReturn();
                    if (r.getStatusCode() == 200) {
                        succeeded.add(each);
                    }
                } catch (Exception ignored) {
                    // a failed request simply does not count as a successful debit
                } finally {
                    done.countDown();
                }
            });
            pool.add(t);
            t.start();
        }
        start.countDown();
        done.await();
        long totalSpent = succeeded.stream().mapToLong(Long::longValue).sum();
        ctx.bag().put("totalSpent", totalSpent);
    }

    // ── Then ──────────────────────────────────────────────────────────
    @Then("系統回應 HTTP {int} 並回傳加值後的最新錢包餘額")
    public void http_with_topped_up_wallet(int status) {
        assertEquals(status, ctx.status());
        assertNotNull(ctx.response().jsonPath().get("wallet.cash"), "wallet balance returned");
    }

    @Then("系統在同一交易寫入一筆原因為 topup 的錢包異動記錄")
    public void topup_ledger_written() {
        assertTrue(ctx.count("wallet.wallet_ledger", "reason = ?", "topup") >= 1,
                "a topup ledger entry must be written in the same transaction");
    }

    @Then("系統確認儲值成功並回傳最新錢包餘額")
    public void topup_success_with_wallet() {
        assertEquals(200, ctx.status());
        assertNotNull(ctx.response().jsonPath().get("wallet.cash"));
    }

    @Then("系統確認儲值成功")
    public void topup_success() {
        assertEquals(200, ctx.status());
    }

    @Then("異動後的帳本餘額等於儲值前餘額加上本次金額")
    public void ledger_balance_equals_before_plus_amount() {
        long before = (long) ctx.bag().get("beforeCash");
        long amount = (long) ctx.bag().get("topupAmount");
        long after = ctx.response().jsonPath().getLong("wallet.cash");
        assertEquals(before + amount, after, "balance must reflect the credit exactly");
    }

    @Then("回傳的最新餘額反映本次加值")
    public void returned_balance_reflects_topup() {
        long amount = (long) ctx.bag().get("topupAmount");
        long after = ctx.response().jsonPath().getLong("wallet.cash");
        assertTrue(after >= amount, "returned balance must include this credit");
    }

    @Then("系統回應 HTTP {int} 並回傳扣款後的最新錢包餘額")
    public void http_with_debited_wallet(int status) {
        assertEquals(status, ctx.status());
        long before = (long) ctx.bag().get("beforeCash");
        long after = ctx.response().jsonPath().getLong("wallet.cash");
        assertTrue(after < before, "balance must be lower after a successful debit");
    }

    @Then("系統寫入一筆負異動的錢包帳本記錄")
    public void negative_ledger_written() {
        assertTrue(ctx.count("wallet.wallet_ledger", "delta < 0") >= 1,
                "a negative (debit) ledger entry must be written");
    }

    @Then("回應附帶使用者當前的錢包餘額")
    public void response_carries_current_wallet() {
        assertNotNull(ctx.response().jsonPath().get("wallet.cash"),
                "402 insufficient-balance body carries the current wallet (API.md §5.1)");
    }

    @Then("系統不寫入任何錢包異動記錄")
    public void no_ledger_written() {
        long before = (long) ctx.bag().getOrDefault("ledgerBefore", 0L);
        assertEquals(before, ctx.count("wallet.wallet_ledger"),
                "a rejected operation must not add a ledger row");
    }

    @Then("系統不變更任何錢包餘額")
    public void no_balance_change() {
        long before = (long) ctx.bag().getOrDefault("ledgerBefore", 0L);
        assertEquals(before, ctx.count("wallet.wallet_ledger"),
                "a rejected operation must not mutate the wallet (no new ledger row)");
    }

    @Then("所有成功扣款的總和不超過初始餘額")
    public void total_spent_within_initial() {
        long initial = (long) ctx.bag().get("initialCash");
        long total = (long) ctx.bag().get("totalSpent");
        assertTrue(total <= initial, "sum of successful debits must not exceed the starting balance");
    }

    @Then("最終餘額等於初始餘額減去所有成功扣款的總和")
    public void final_balance_matches() {
        long initial = (long) ctx.bag().get("initialCash");
        long total = (long) ctx.bag().get("totalSpent");
        long finalCash = currentCash();
        assertEquals(initial - total, finalCash, "final balance == initial − Σ successful debits");
    }

    @Then("錢包餘額與帳本累積完全一致且無負餘額")
    public void wallet_consistent_with_ledger() {
        long finalCash = currentCash();
        Long ledgerSum = ctx.jdbc().queryForObject(
                "SELECT COALESCE(SUM(delta),0) FROM wallet.wallet_ledger WHERE currency = 'cash'", Long.class);
        assertNotNull(ledgerSum);
        assertEquals(ledgerSum.longValue(), finalCash, "wallet balance must equal the cash ledger sum");
        assertTrue(finalCash >= 0, "balance must never go negative");
    }

    // ── helpers ───────────────────────────────────────────────────────
    private void rememberLedger() {
        ctx.bag().put("ledgerBefore", ctx.count("wallet.wallet_ledger"));
    }

    private long currentCash() {
        Response r = ctx.get("/auth/me");
        if (r.getStatusCode() == 200) {
            Long c = r.jsonPath().getLong("wallet.cash");
            return c == null ? 0L : c;
        }
        return (long) ctx.bag().getOrDefault("cash", 0L);
    }
}
