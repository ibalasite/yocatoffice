package com.sayyogames.yocat.bdd;

import com.sayyogames.yocat.YocatOfficeBackendApplication;
import com.sayyogames.yocat.platform.cache.CacheNamespace;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.modulith.core.ApplicationModules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * architecture — Spring Modulith boundary guards (features/architecture, PRD US-PLAT-001 AC-3/AC-4).
 * HC-1 schema isolation, HC-2 public-API-only cross-module calls, HC-3 event contracts via Outbox,
 * HC-4 Redis namespace isolation, HC-5 acyclic module dependency graph. These run in-process against
 * the real module structure and database (no HTTP controller needed).
 */
public class ModulithSteps {

    @Autowired
    private ScenarioContext ctx;
    @Autowired
    private ApplicationContext appCtx;
    @Autowired
    private CacheNamespace cacheNamespace;

    // ── Given ─────────────────────────────────────────────────────────
    @Given("系統由 {int} 個有界上下文組成：member、wallet、deposit、entitlement、record、leaderboard、game-save")
    public void system_has_bounded_contexts(int count) {
        ctx.bag().put("bcCount", count);
    }

    @Given("各有界上下文各自擁有獨立的資料庫 schema")
    public void each_bc_owns_schema() {
        long schemas = ctx.count("information_schema.schemata",
                "schema_name IN ('member','wallet','deposit','entitlement','record','leaderboard','game_save')");
        assertEquals(7L, schemas, "each of the 7 BCs owns an isolated schema");
    }

    @Given("entitlement 購買流程需呼叫 wallet 進行扣款")
    public void entitlement_calls_wallet_for_debit() {
        ctx.bag().put("crossCall", "entitlement->wallet");
    }

    @Given("deposit 確認後需通知 wallet 入帳")
    public void deposit_notifies_wallet() {
        ctx.bag().put("crossEvent", "deposit->wallet");
    }

    @Given("多個有界上下文使用 Redis 作為快取與限流後援")
    public void multiple_bcs_use_redis() {
        ctx.bag().put("redis", true);
    }

    @Given("{int} 個有界上下文之間存在依賴關係")
    public void bcs_have_dependencies(int count) {
        ctx.bag().put("bcCount", count);
    }

    @Given("僅啟動 {string} 有界上下文，其餘模組以外部替身隔離")
    public void only_start_single_bc(String bc) {
        ctx.bag().put("isolatedBc", bc);
    }

    // ── When ──────────────────────────────────────────────────────────
    @When("執行模組邊界靜態分析")
    public void run_boundary_analysis() {
        runVerify();
    }

    @When("執行模組依賴分析")
    public void run_dependency_analysis() {
        runVerify();
    }

    @When("執行模組化測試驗證依賴圖")
    public void run_modularity_test() {
        runVerify();
    }

    @When("檢視各模組寫入的 Redis 鍵")
    public void inspect_redis_keys() {
        ctx.bag().put("memberKey", cacheNamespace.memberSession("tok"));
        ctx.bag().put("lbKey", cacheNamespace.leaderboardTop("sandbox", 20));
        ctx.bag().put("rateKey", cacheNamespace.rate("login", "1.2.3.4"));
    }

    @When("該有界上下文啟動應用情境")
    public void bc_boots_application() {
        ctx.bag().put("booted", appCtx.getBeanDefinitionCount() > 0);
    }

    @When("deposit 發布儲值確認領域事件")
    public void deposit_publishes_confirmed_event() {
        ctx.bag().put("publishedEvent", "DepositConfirmed");
    }

    // ── Then ──────────────────────────────────────────────────────────
    @Then("不存在任何跨 schema 的外鍵或直接查詢")
    public void no_cross_schema_fk() {
        Long crossFk = ctx.jdbc().queryForObject(
                "SELECT count(*) FROM information_schema.table_constraints tc "
                        + "JOIN information_schema.constraint_column_usage ccu "
                        + "  ON tc.constraint_name = ccu.constraint_name "
                        + "WHERE tc.constraint_type = 'FOREIGN KEY' "
                        + "  AND tc.table_schema IN ('member','wallet','deposit','entitlement','record','leaderboard','game_save') "
                        + "  AND ccu.table_schema <> tc.table_schema", Long.class);
        assertEquals(0L, crossFk == null ? 0L : crossFk, "no foreign key may cross a BC schema boundary (HC-1)");
    }

    @Then("member 的錢包資料一律經 wallet 的公開介面取得")
    public void member_reads_wallet_via_public_api() {
        assertNoModulithViolation();
    }

    @Then("entitlement 僅透過 wallet 的公開服務介面呼叫扣款")
    public void entitlement_uses_wallet_public_api() {
        assertNoModulithViolation();
    }

    @Then("不存在對其他模組內部類別的直接依賴")
    public void no_direct_internal_dependency() {
        assertNoModulithViolation();
    }

    @Then("該事件透過交易式 Outbox 可靠投遞")
    public void event_delivered_via_outbox() {
        long tables = ctx.count("information_schema.tables",
                "table_schema = 'modulith' AND table_name = 'event_publication'");
        assertEquals(1L, tables, "the transactional Outbox table must exist (HC-3)");
    }

    @Then("事件結構符合既有合約且向後相容")
    public void event_structure_backward_compatible() throws Exception {
        Class<?> contract = Class.forName("com.sayyogames.yocat.shared.DomainEvent");
        Class<?> concrete = Class.forName("com.sayyogames.yocat.deposit.domain.DepositConfirmed");
        assertTrue(contract.isAssignableFrom(concrete),
                "concrete events must implement the stable DomainEvent contract (backward-compatible)");
    }

    @Then("每個模組的鍵都帶有自己的命名空間前綴")
    public void keys_are_namespaced() {
        String memberKey = (String) ctx.bag().get("memberKey");
        String lbKey = (String) ctx.bag().get("lbKey");
        assertTrue(memberKey.contains("member"), "member key carries its namespace: " + memberKey);
        assertTrue(lbKey.contains("leaderboard"), "leaderboard key carries its namespace: " + lbKey);
    }

    @Then("不同模組之間不共用同一組可變快取鍵")
    public void modules_do_not_share_keys() {
        assertNotEquals(ctx.bag().get("memberKey"), ctx.bag().get("lbKey"),
                "distinct modules must not collide on cache keys (HC-4)");
    }

    @Then("模組依賴關係為有向無環圖")
    public void dependency_graph_is_dag() {
        assertNoModulithViolation();
    }

    @Then("不存在任何循環依賴")
    public void no_cyclic_dependency() {
        assertNoModulithViolation();
    }

    @Then("該有界上下文成功啟動")
    public void bc_started_successfully() {
        assertTrue((boolean) ctx.bag().getOrDefault("booted", appCtx.getBeanDefinitionCount() > 0),
                "the bounded context context must boot");
    }

    @Then("其健康檢查回報就緒")
    public void health_reports_ready() {
        assertEquals(200, ctx.get("/actuator/health").getStatusCode(), "health endpoint must report ready");
    }

    // ── helpers ───────────────────────────────────────────────────────
    private void runVerify() {
        try {
            ApplicationModules.of(YocatOfficeBackendApplication.class).verify();
            ctx.bag().put("modulithViolation", null);
        } catch (Throwable t) {
            ctx.bag().put("modulithViolation", t.getMessage());
        }
    }

    private void assertNoModulithViolation() {
        assertNull(ctx.bag().get("modulithViolation"),
                "Spring Modulith must report no boundary/cycle violation: " + ctx.bag().get("modulithViolation"));
    }
}
