package com.sayyogames.yocat.e2e;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import com.sayyogames.yocat.YocatOfficeBackendApplication;
import com.sayyogames.yocat.persistence.support.PostgresTestSupport;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static com.sayyogames.yocat.persistence.support.PostgresTestSupport.POSTGRES;

/**
 * End-to-end smoke test (O-26, Harden phase) — drives the complete critical user journey against a
 * <b>real, fully booted application instance</b>: the actual {@link YocatOfficeBackendApplication} on an
 * embedded random-port web server, a real {@code postgres:16} with the verbatim {@code migrations/} DDL+seed, and a
 * real {@code redis:7.2} backing the rate limiter — no mocks, no DB shortcuts, every hop over real HTTP.
 *
 * <p>Journey (test-plan §3.3 Critical User Flows "Smoke=Yes" rows, API-layer blackbox per api-only):
 * <pre>
 *   register → login → GET /auth/me
 *     → topup → spend → buy entitlement → GET /entitlements
 *     → PUT /save → GET /save (cross-device restore)
 *     → POST /records → GET /records
 *     → POST /leaderboard → GET /leaderboard
 *     → overdraw spend → 402 餘額不足 (balance unchanged, rolled back)
 * </pre>
 *
 * <p>This is the deployment-grade counterpart of {@code scripts/local-setup.sh}'s register→topup→spend→402 smoke,
 * extended to the full journey. The reusable, ops-runnable version (against a deployed ingress/NodePort) lives at
 * {@code tests/e2e/smoke.spec.ts} (Playwright API mode, test-plan §3.3 framework choice); this IT proves the same
 * journey in-process so the smoke is verified on every build without external infrastructure.</p>
 *
 * <p>Order-independent: it seeds exactly the reference catalog rows it consumes (idempotent upsert, mirroring
 * {@code bdd/Hooks}) and uses a unique per-run account, so it passes regardless of the shared container's prior
 * state when the whole {@code *IT} suite runs together.</p>
 */
@SpringBootTest(
        classes = YocatOfficeBackendApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
                "spring.flyway.enabled=false",
                "spring.modulith.events.jdbc.schema-initialization.enabled=false"
        })
@Testcontainers
@DisplayName("E2E smoke — full critical user journey against a real running app")
class E2eSmokeJourneyIT {

    /** Real Redis 7.2 so the EDD §11.3C rate limiter runs the production path (not fail-open). */
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.2")).withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        PostgresTestSupport.ensureStartedAndMigrated();
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        if (!REDIS.isRunning()) {
            REDIS.start();
        }
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
        // Order-independent: ensure the reference catalog rows this journey consumes exist, whether or not a
        // prior BDD scenario truncated the shared container. Idempotent (ON CONFLICT DO NOTHING).
        jdbc.update("INSERT INTO deposit.deposit_pack(pack_id, currency, amount, label, bonus_pct, is_active) "
                + "VALUES ('g_big','gems',700,'大寶石包',16,TRUE) ON CONFLICT (pack_id) DO NOTHING");
        jdbc.update("INSERT INTO entitlement.entitlement_catalog"
                + "(item_id, kind, label, price_currency, price_amount, is_active) "
                + "VALUES ('skin_ceo_gold','cosmetic','黃金執行長外觀','gems',120,TRUE) "
                + "ON CONFLICT (item_id) DO NOTHING");
        jdbc.update("INSERT INTO record.record_type_catalog(type_code, label, is_active) "
                + "VALUES ('achievement','成就',TRUE) ON CONFLICT (type_code) DO NOTHING");
        jdbc.update("INSERT INTO leaderboard.leaderboard_mode_catalog(mode, label, is_active) "
                + "VALUES ('sandbox','沙盒模式',TRUE) ON CONFLICT (mode) DO NOTHING");
    }

    @Test
    @DisplayName("register→login→topup→spend→buy→save→record→leaderboard→402, end to end")
    void fullCriticalUserJourney() {
        String email = "e2e-smoke+" + System.nanoTime() + "@yocat-office-backend.local";
        String password = "Password1!";

        // ── 1. register → account + wallet(0) + token (US-MEM-001/AC-1) ──
        Response reg = given().contentType(ContentType.JSON)
                .body(Map.of("email", email, "displayName", "E2E Smoke", "password", password))
                .when().post("/auth/register")
                .then().statusCode(200)
                .body("token", org.hamcrest.Matchers.notNullValue())
                .body("wallet.gems", org.hamcrest.Matchers.is(0))
                .body("wallet.cash", org.hamcrest.Matchers.is(0))
                .extract().response();
        String regToken = reg.jsonPath().getString("token");
        assertThat(regToken).isNotBlank();

        // ── 2. login → fresh session token (US-MEM-001/AC-4) ──
        String token = given().contentType(ContentType.JSON)
                .body(Map.of("email", email, "password", password))
                .when().post("/auth/login")
                .then().statusCode(200)
                .body("token", org.hamcrest.Matchers.notNullValue())
                .body("account.email", org.hamcrest.Matchers.equalTo(email))
                .extract().jsonPath().getString("token");
        assertThat(token).isNotBlank();

        // ── 3. GET /auth/me authenticated (US-MEM-001/AC-4) ──
        given().header("Authorization", "Bearer " + token)
                .when().get("/auth/me")
                .then().statusCode(200)
                .body("account.email", org.hamcrest.Matchers.equalTo(email))
                .body("wallet.gems", org.hamcrest.Matchers.is(0));

        // ── 4. topup g_big → wallet gems = 700 (US-WAL-001/AC-1) ──
        given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON)
                .body(Map.of("pack", "g_big"))
                .when().post("/wallet/topup")
                .then().statusCode(200)
                .body("ok", org.hamcrest.Matchers.is(true))
                .body("wallet.gems", org.hamcrest.Matchers.is(700));

        // ── 5. spend 10 gems → balance 690 (US-WAL-001/AC-2, server-authoritative) ──
        given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON)
                .body(Map.of("currency", "gems", "amount", 10, "reason", "smoke"))
                .when().post("/wallet/spend")
                .then().statusCode(200)
                .body("ok", org.hamcrest.Matchers.is(true))
                .body("wallet.gems", org.hamcrest.Matchers.is(690));

        // ── 6. buy entitlement skin_ceo_gold for 120 gems → atomic debit+grant, balance 570 (US-ENT-001/AC-1) ──
        given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON)
                .body(Map.of("kind", "cosmetic", "itemId", "skin_ceo_gold", "currency", "gems", "amount", 120))
                .when().post("/entitlements/buy")
                .then().statusCode(200)
                .body("ok", org.hamcrest.Matchers.is(true))
                .body("wallet.gems", org.hamcrest.Matchers.is(570))
                .body("entitlements.itemId", org.hamcrest.Matchers.hasItem("skin_ceo_gold"));

        // ── 7. GET /entitlements → owns skin_ceo_gold (US-ENT-001/AC-1) ──
        List<String> owned = given().header("Authorization", "Bearer " + token)
                .when().get("/entitlements")
                .then().statusCode(200)
                .extract().jsonPath().getList("itemId");
        assertThat(owned).contains("skin_ceo_gold");

        // ── 8. PUT /save → GET /save: cross-device restore round-trip (US-SAVE-001/AC-5) ──
        given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON)
                .body(Map.of("slot", "main", "data", Map.of("level", 7, "cash", 12345, "office", "hq")))
                .when().put("/save")
                .then().statusCode(200)
                .body("ok", org.hamcrest.Matchers.is(true));
        given().header("Authorization", "Bearer " + token)
                .when().get("/save").then().statusCode(200)
                .body("save.data.level", org.hamcrest.Matchers.is(7))
                .body("save.data.office", org.hamcrest.Matchers.equalTo("hq"));

        // ── 9. POST /records → GET /records: append-only business history (US-SAVE-001 sibling) ──
        given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON)
                .body(Map.of("type", "achievement", "payload", Map.of("name", "first_million", "value", 1000000)))
                .when().post("/records")
                .then().statusCode(200)
                .body("ok", org.hamcrest.Matchers.is(true));
        given().header("Authorization", "Bearer " + token)
                .when().get("/records")
                .then().statusCode(200)
                .body("type", org.hamcrest.Matchers.hasItem("achievement"));

        // ── 10. POST /leaderboard → GET /leaderboard: attributed submit + public Top-N ──
        given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON)
                .body(Map.of("runId", "run-" + System.nanoTime(), "mode", "sandbox", "score", 98765, "grade", "S"))
                .when().post("/leaderboard")
                .then().statusCode(200)
                .body("ok", org.hamcrest.Matchers.is(true));
        given().queryParam("mode", "sandbox")
                .when().get("/leaderboard")
                .then().statusCode(200)
                .body("score", org.hamcrest.Matchers.hasItem(98765));

        // ── 11. overdraw spend (1,000,000 gems ≫ 570, within [1,10,000,000] valid range) → 402 餘額不足,
        //        balance unchanged (rolled back) (US-WAL-001/AC-3) ──
        given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON)
                .body(Map.of("currency", "gems", "amount", 1_000_000L, "reason", "smoke-overdraw"))
                .when().post("/wallet/spend")
                .then().statusCode(402)
                .body("error", org.hamcrest.Matchers.equalTo("餘額不足"))
                .body("wallet.gems", org.hamcrest.Matchers.is(570));

        // ── 12. final reconciliation via /auth/me: balance 570, entitlement still owned ──
        given().header("Authorization", "Bearer " + token)
                .when().get("/auth/me")
                .then().statusCode(200)
                .body("wallet.gems", org.hamcrest.Matchers.is(570))
                .body("entitlements.itemId", org.hamcrest.Matchers.hasItem("skin_ceo_gold"));
    }
}
