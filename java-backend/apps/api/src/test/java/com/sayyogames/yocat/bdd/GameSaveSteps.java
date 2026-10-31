package com.sayyogames.yocat.bdd;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * game-save BC — cross-device office-save sync (API.md §3 game-save: PUT /save, GET /save).
 * PRD US-SAVE-001: (account_id, slot) upsert, missing {@code data} → 400, GET of an empty slot returns
 * {@code {"save": null}} with 200 (not 404), 4MB body cap.
 */
public class GameSaveSteps {

    @Autowired
    private ScenarioContext ctx;

    // ── Given ─────────────────────────────────────────────────────────
    @Given("該帳號的預設存檔槽已有一份存檔")
    public void default_slot_has_a_save() {
        ctx.jdbc().update(
                "INSERT INTO game_save.saves(account_id, slot, data) VALUES (?, 'main', ?::jsonb) "
                        + "ON CONFLICT (account_id, slot) DO UPDATE SET data = EXCLUDED.data",
                ctx.accountId(), "{\"office\":{\"floor\":3}}");
    }

    @Given("該帳號的預設存檔槽尚無任何存檔")
    public void default_slot_empty() {
        assertEquals(0L, ctx.count("game_save.saves", "account_id = ?", ctx.accountId()),
                "precondition: no save yet");
    }

    @Given("使用者已在裝置 A 上傳了一份辦公室存檔")
    public void device_a_uploaded_a_save() {
        String data = "{\"office\":{\"floor\":7,\"staff\":12},\"device\":\"A\"}";
        ctx.bag().put("deviceAData", data);
        ctx.bag().put("email", ctx.bag().get("email"));
        ctx.put("/save", "{\"slot\":\"main\",\"data\":" + data + "}");
    }

    // ── When ──────────────────────────────────────────────────────────
    @When("使用者將辦公室存檔上傳至預設存檔槽")
    public void upload_save_to_default_slot() {
        rememberSaves();
        ctx.put("/save", "{\"slot\":\"main\",\"data\":{\"office\":{\"floor\":1}}}");
    }

    @When("使用者在未提供存檔資料的情況下上傳存檔")
    public void upload_without_data() {
        rememberSaves();
        ctx.put("/save", "{\"slot\":\"main\"}");
    }

    @When("使用者上傳一個內容為空物件的存檔")
    public void upload_empty_object() {
        rememberSaves();
        ctx.put("/save", "{\"slot\":\"main\",\"data\":{}}");
    }

    @When("使用者嘗試上傳存檔")
    public void try_upload_unauth() {
        rememberSaves();
        ctx.put("/save", "{\"slot\":\"main\",\"data\":{}}");
    }

    @When("使用者查詢預設存檔槽")
    public void query_default_slot() {
        ctx.get("/save");
    }

    @When("使用者在裝置 B 以同一帳號登入並查詢存檔")
    public void device_b_login_and_query() {
        ctx.login((String) ctx.bag().get("email"), "s3cret!");
        ctx.get("/save");
    }

    // ── Then ──────────────────────────────────────────────────────────
    @Then("系統回應 HTTP {int} 並確認存檔已寫入")
    public void save_written_confirmed(int status) {
        assertEquals(status, ctx.status());
    }

    @Then("該存檔的最後更新時間被更新為當下")
    public void updated_at_is_recent() {
        Long fresh = ctx.jdbc().query(
                "SELECT CASE WHEN updated_at > now() - interval '2 minutes' THEN 1 ELSE 0 END "
                        + "FROM game_save.saves WHERE account_id = ? AND slot = 'main'",
                (rs, n) -> rs.getLong(1), ctx.accountId()).stream().findFirst().orElse(0L);
        assertEquals(1L, fresh, "updated_at must be refreshed to now");
    }

    @Then("系統確認存檔已寫入")
    public void save_confirmed() {
        assertEquals(200, ctx.status());
    }

    @Then("後續查詢可取回該空物件存檔")
    public void can_retrieve_empty_object_save() {
        var r = ctx.get("/save");
        assertEquals(200, r.getStatusCode());
        assertNotEquals("null", String.valueOf((Object) r.jsonPath().get("save")),
                "an empty-object save is still a real save (not null)");
    }

    @Then("系統回應 HTTP {int} 並回傳最新存檔資料與更新時間")
    public void http_with_latest_save(int status) {
        assertEquals(status, ctx.status());
        var jp = ctx.response().jsonPath();
        assertTrue(jp.get("save") != null || jp.get("data") != null, "save data returned");
        assertTrue(jp.get("updatedAt") != null || jp.get("updated_at") != null, "update time returned");
    }

    @Then("系統回應 HTTP {int} 且存檔內容為空")
    public void http_with_empty_save(int status) {
        assertEquals(status, ctx.status());
        assertEquals("null", String.valueOf((Object) ctx.response().jsonPath().get("save")),
                "empty slot returns {save:null} (not 404)");
    }

    @Then("系統不回應資源不存在錯誤")
    public void no_not_found_error() {
        assertNotEquals(404, ctx.status(), "an empty slot must not be a 404");
    }

    @Then("系統回傳裝置 A 寫入的最新存檔內容")
    public void returns_device_a_content() {
        Integer floor = ctx.response().jsonPath().get("save.office.floor");
        if (floor == null) {
            floor = ctx.response().jsonPath().get("data.office.floor");
        }
        assertEquals(Integer.valueOf(7), floor, "device B must see device A's latest save (floor=7)");
    }

    @Then("兩台裝置看到的存檔資料一致")
    public void both_devices_consistent() {
        Integer floor = ctx.jdbc().query(
                "SELECT (data->'office'->>'floor')::int FROM game_save.saves WHERE account_id = ? AND slot='main'",
                (rs, n) -> rs.getInt(1), ctx.accountId()).stream().findFirst().orElse(null);
        assertEquals(Integer.valueOf(7), floor, "the single authoritative save is identical across devices");
    }

    @Then("系統不寫入任何存檔")
    public void no_save_written() {
        long before = (long) ctx.bag().getOrDefault("savesBefore", 0L);
        assertEquals(before, ctx.count("game_save.saves"), "a rejected upload must not persist a save");
    }

    // ── helpers ───────────────────────────────────────────────────────
    private void rememberSaves() {
        ctx.bag().put("savesBefore", ctx.count("game_save.saves"));
    }
}
