package com.sayyogames.yocat.bdd;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * leaderboard BC — public Top-N query &amp; attributed submit (API.md §3 leaderboard: GET /leaderboard,
 * POST /leaderboard). PRD US-LB-001: public read (no token), limit clamped to [1,100], run_id upsert.
 */
public class LeaderboardSteps {

    @Autowired
    private ScenarioContext ctx;

    // ── Background / Given ────────────────────────────────────────────
    @Given("排行榜已有若干筆沙盒模式的既有分數")
    public void some_existing_sandbox_scores() {
        seedScores(5, 1000);
    }

    @Given("排行榜已有超過 {int} 筆分數")
    public void more_than_n_scores(int n) {
        seedScores(n + 20, 500);
    }

    @Given("使用者已以場次 {string} 提交過分數")
    public void already_submitted_run(String runId) {
        long acct = ctx.accountId();
        ctx.jdbc().update(
                "INSERT INTO leaderboard.leaderboard(run_id, account_id, display_name, mode, score) "
                        + "VALUES (?,?,?, 'sandbox', ?) "
                        + "ON CONFLICT (run_id) DO UPDATE SET score = EXCLUDED.score",
                runId, acct, "喵喵科技", 500_000L);
        ctx.bag().put("runId", runId);
        ctx.bag().put("oldScore", 500_000L);
    }

    // ── When ──────────────────────────────────────────────────────────
    @When("使用者查詢排行榜前 {int} 名")
    public void query_top_n(int n) {
        ctx.get("/leaderboard?limit=" + n);
    }

    @When("使用者以筆數 {string} 查詢排行榜")
    public void query_with_limit(String limit) {
        ctx.get("/leaderboard?limit=" + limit);
    }

    @When("使用者以場次 {string} 提交分數 {int}")
    public void submit_score(String runId, int score) {
        ctx.bag().put("lbBefore", ctx.count("leaderboard.leaderboard"));
        ctx.bag().put("runId", runId);
        ctx.post("/leaderboard", "{\"runId\":\"" + runId + "\",\"mode\":\"sandbox\",\"score\":" + score + "}");
    }

    @When("使用者以相同場次 {string} 提交更高的分數")
    public void submit_higher_score(String runId) {
        long higher = (long) ctx.bag().getOrDefault("oldScore", 0L) + 100_000L;
        ctx.bag().put("newScore", higher);
        ctx.post("/leaderboard", "{\"runId\":\"" + runId + "\",\"mode\":\"sandbox\",\"score\":" + higher + "}");
    }

    @When("使用者在未提供場次 ID 的情況下提交分數")
    public void submit_without_run_id() {
        ctx.bag().put("lbBefore", ctx.count("leaderboard.leaderboard"));
        ctx.post("/leaderboard", "{\"mode\":\"sandbox\",\"score\":123}");
    }

    @When("使用者嘗試提交分數上榜")
    public void try_submit_unauth() {
        ctx.bag().put("lbBefore", ctx.count("leaderboard.leaderboard"));
        ctx.post("/leaderboard", "{\"runId\":\"run-anon\",\"mode\":\"sandbox\",\"score\":123}");
    }

    // ── Then ──────────────────────────────────────────────────────────
    @Then("系統回應 HTTP {int} 並依分數由高到低回傳上榜清單")
    public void http_with_sorted_list(int status) {
        assertEquals(status, ctx.status());
        List<Integer> scores = ctx.response().jsonPath().getList("score");
        assertNotNull(scores, "leaderboard entries must carry score");
        for (int i = 1; i < scores.size(); i++) {
            assertTrue(scores.get(i - 1) >= scores.get(i), "must be ordered by score DESC");
        }
    }

    @Then("每筆上榜資料包含場次 ID、顯示名稱、模式與分數")
    public void entry_fields_present() {
        List<java.util.Map<String, Object>> rows = ctx.response().jsonPath().getList("$");
        assertFalse(rows == null || rows.isEmpty(), "entries present");
        for (var row : rows) {
            assertTrue(row.containsKey("runId") || row.containsKey("run_id"), "runId present");
            assertTrue(row.containsKey("displayName") || row.containsKey("display_name"), "displayName present");
            assertTrue(row.containsKey("mode"), "mode present");
            assertTrue(row.containsKey("score"), "score present");
        }
    }

    @Then("系統回傳的筆數不超過 {string}")
    public void returned_count_within(String maxReturned) {
        int max = Integer.parseInt(maxReturned);
        List<?> rows = ctx.response().jsonPath().getList("$");
        assertNotNull(rows);
        assertTrue(rows.size() <= max, "returned rows must be clamped to " + max);
    }

    @Then("系統回應 HTTP {int} 並確認上榜成功")
    public void submit_confirmed(int status) {
        assertEquals(status, ctx.status());
    }

    @Then("該場次以使用者的顯示名稱歸戶記錄")
    public void attributed_to_display_name() {
        String name = ctx.jdbc().query(
                "SELECT display_name FROM leaderboard.leaderboard WHERE run_id = ?",
                (rs, n) -> rs.getString(1), (String) ctx.bag().get("runId")).stream().findFirst().orElse(null);
        assertEquals("喵喵科技", name, "the run must be attributed with the member display name snapshot");
    }

    @Then("該場次的排行記錄更新為最新分數")
    public void run_updated_to_latest_score() {
        Long score = ctx.jdbc().query(
                "SELECT score FROM leaderboard.leaderboard WHERE run_id = ?",
                (rs, n) -> rs.getLong(1), (String) ctx.bag().get("runId")).stream().findFirst().orElse(null);
        assertEquals(ctx.bag().get("newScore"), score, "run score must be updated to the latest higher value");
    }

    @Then("排行榜中該場次仍只有一筆記錄")
    public void run_has_single_row() {
        assertEquals(1L, ctx.count("leaderboard.leaderboard", "run_id = ?", (String) ctx.bag().get("runId")),
                "run_id upsert must keep exactly one row");
    }

    @Then("系統不寫入任何排行記錄")
    public void no_leaderboard_row_written() {
        long before = (long) ctx.bag().getOrDefault("lbBefore", 0L);
        assertEquals(before, ctx.count("leaderboard.leaderboard"), "a rejected submit must not add a row");
    }

    // ── helpers ───────────────────────────────────────────────────────
    private void seedScores(int count, long base) {
        for (int i = 0; i < count; i++) {
            ctx.jdbc().update(
                    "INSERT INTO leaderboard.leaderboard(run_id, account_id, display_name, mode, score) "
                            + "VALUES (?,?,?, 'sandbox', ?) ON CONFLICT (run_id) DO NOTHING",
                    "seed-" + i, 9_000_000L + i, "既有玩家" + i, base + i);
        }
    }
}
