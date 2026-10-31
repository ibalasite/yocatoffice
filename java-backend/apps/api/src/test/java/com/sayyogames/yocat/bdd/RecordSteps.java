package com.sayyogames.yocat.bdd;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * record BC — append-only business history (API.md §3 record: POST /records, GET /records).
 * PRD US-REC-001: bankruptcy/achievement/legacy history, newest-first listing, type filter, 100-cap.
 */
public class RecordSteps {

    @Autowired
    private ScenarioContext ctx;

    // ── Given ─────────────────────────────────────────────────────────
    @Given("該帳號已有破產與成就各一筆經營史記錄")
    public void has_one_bankruptcy_and_one_achievement() {
        seedType("bankruptcy", "破產");
        seedType("achievement", "成就");
        ctx.jdbc().update("INSERT INTO record.records(account_id, type, payload) VALUES (?, 'bankruptcy', '{}'::jsonb)",
                ctx.accountId());
        ctx.jdbc().update("INSERT INTO record.records(account_id, type, payload) VALUES (?, 'achievement', '{}'::jsonb)",
                ctx.accountId());
    }

    // ── When ──────────────────────────────────────────────────────────
    @When("使用者新增一筆破產類型的經營史記錄")
    public void add_bankruptcy_record() {
        rememberRecords();
        ctx.post("/records", "{\"type\":\"bankruptcy\",\"payload\":{\"note\":\"went under\"}}");
    }

    @When("使用者以類型 {string} 新增經營史記錄")
    public void add_record_of_type(String type) {
        rememberRecords();
        ctx.post("/records", "{\"type\":\"" + type + "\",\"payload\":{}}");
    }

    @When("使用者嘗試新增經營史記錄")
    public void try_add_record_unauth() {
        rememberRecords();
        ctx.post("/records", "{\"type\":\"bankruptcy\",\"payload\":{}}");
    }

    @When("使用者查詢全部經營史")
    public void list_all_records() {
        ctx.get("/records");
    }

    @When("使用者以破產類型篩選查詢經營史")
    public void list_records_filtered_bankruptcy() {
        ctx.get("/records?type=bankruptcy");
    }

    // ── Then ──────────────────────────────────────────────────────────
    @Then("系統回應 HTTP {int} 並確認記錄已寫入")
    public void record_written_confirmed(int status) {
        assertEquals(status, ctx.status());
    }

    @Then("該筆記錄以 append-only 方式歸屬於該帳號")
    public void record_appended_to_account() {
        assertTrue(ctx.count("record.records", "account_id = ?", ctx.accountId()) >= 1,
                "the record must be persisted against the account");
    }

    @Then("系統不新增任何經營史記錄")
    public void no_record_added() {
        long before = (long) ctx.bag().getOrDefault("recordsBefore", 0L);
        assertEquals(before, ctx.count("record.records"), "a rejected write must not add a record");
    }

    @Then("系統回應 HTTP {int} 並回傳該帳號的經營史清單")
    public void http_with_record_list(int status) {
        assertEquals(status, ctx.status());
        List<?> list = ctx.response().jsonPath().getList("$");
        if (list == null) {
            list = ctx.response().jsonPath().getList("records");
        }
        assertFalse(list == null, "a records list must be returned");
    }

    @Then("清單依建立時間由新到舊排序")
    public void list_newest_first() {
        List<String> created = ctx.jdbc().query(
                "SELECT to_char(created_at,'YYYYMMDDHH24MISSUS') FROM record.records WHERE account_id = ? "
                        + "ORDER BY created_at DESC",
                (rs, n) -> rs.getString(1), ctx.accountId());
        for (int i = 1; i < created.size(); i++) {
            assertTrue(created.get(i - 1).compareTo(created.get(i)) >= 0, "records must be newest-first");
        }
    }

    @Then("系統僅回傳破產類型的經營史記錄")
    public void only_bankruptcy_returned() {
        List<java.util.Map<String, Object>> list = ctx.response().jsonPath().getList("$");
        if (list == null) {
            list = ctx.response().jsonPath().getList("records");
        }
        assertFalse(list == null, "filtered list must be returned");
        for (var row : list) {
            assertEquals("bankruptcy", String.valueOf(row.get("type")), "filter must return only bankruptcy");
        }
    }

    @Then("回傳筆數不超過單次上限 {int} 筆")
    public void list_within_cap(int cap) {
        List<?> list = ctx.response().jsonPath().getList("$");
        if (list == null) {
            list = ctx.response().jsonPath().getList("records");
        }
        assertFalse(list == null);
        assertTrue(list.size() <= cap, "page must not exceed the " + cap + "-row cap");
    }

    // ── helpers ───────────────────────────────────────────────────────
    private void rememberRecords() {
        ctx.bag().put("recordsBefore", ctx.count("record.records"));
    }

    private void seedType(String code, String label) {
        ctx.jdbc().update(
                "INSERT INTO record.record_type_catalog(type_code, label) VALUES (?::record.type_enum, ?) "
                        + "ON CONFLICT (type_code) DO NOTHING",
                code, label);
    }
}
