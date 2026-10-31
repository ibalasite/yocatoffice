package com.sayyogames.yocat.bdd;

import io.cucumber.spring.ScenarioScope;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;

/**
 * Per-scenario shared state + real HTTP/JDBC helpers for the BDD step definitions.
 *
 * <p>One fresh instance per scenario ({@link ScenarioScope}) so scenarios never leak state into each
 * other (Review #9 Cleanup / no order-dependence). Every "When" drives the real REST API over HTTP
 * (RestAssured against the random port set in {@link Hooks}); seeding and negative-state assertions
 * read/write the real Testcontainers PostgreSQL through {@link JdbcTemplate} — no business logic is
 * mocked (Review #2 / Anti-Fake red line).</p>
 */
@Component
@ScenarioScope
public class ScenarioContext {

    @Autowired
    JdbcTemplate jdbc;

    /** Opaque Bearer session token of the "current" logged-in user; {@code null} = unauthenticated. */
    private String token;
    /** Last HTTP response produced by a When step — every Then asserts against this. */
    private Response response;
    /** Idempotency-Key reused across replay scenarios. */
    private String idempotencyKey;
    /** Free-form carrier for scenario-local facts (balances, ids, counts, device snapshots, …). */
    private final Map<String, Object> bag = new HashMap<>();

    // ── session / token ───────────────────────────────────────────────
    public String token() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public void clearToken() {
        this.token = null;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }

    public String newIdempotencyKey() {
        this.idempotencyKey = UUID.randomUUID().toString();
        return this.idempotencyKey;
    }

    public void setIdempotencyKey(String key) {
        this.idempotencyKey = key;
    }

    public Map<String, Object> bag() {
        return bag;
    }

    // ── HTTP ──────────────────────────────────────────────────────────
    /** Base request spec carrying the Bearer token when one is present. */
    public RequestSpecification http() {
        RequestSpecification spec = given().contentType(JSON).accept(JSON);
        if (token != null) {
            spec = spec.header("Authorization", "Bearer " + token);
        }
        return spec;
    }

    public Response post(String path, String jsonBody) {
        this.response = http().body(jsonBody).post(path).andReturn();
        return this.response;
    }

    public Response put(String path, String jsonBody) {
        this.response = http().body(jsonBody).put(path).andReturn();
        return this.response;
    }

    public Response get(String path) {
        this.response = http().get(path).andReturn();
        return this.response;
    }

    public Response response() {
        return response;
    }

    public void setResponse(Response response) {
        this.response = response;
    }

    public int status() {
        return response == null ? -1 : response.getStatusCode();
    }

    // ── high-level real-API seeding ───────────────────────────────────
    /**
     * Registers an account through the real {@code POST /auth/register} endpoint and remembers the
     * issued session token. Used by Background "existing account" / "logged-in user" givens so the
     * setup itself exercises the production path (no direct password-hash fabrication).
     */
    public Response registerAccount(String email, String displayName, String password) {
        Response r = given().contentType(JSON).accept(JSON)
                .body(String.format("{\"email\":\"%s\",\"displayName\":\"%s\",\"password\":\"%s\"}",
                        email, displayName, password))
                .post("/auth/register").andReturn();
        if (r.getStatusCode() == 200) {
            this.token = r.jsonPath().getString("token");
        }
        this.response = r;
        return r;
    }

    /** Logs in through the real {@code POST /auth/login} endpoint and remembers the new token. */
    public Response login(String email, String password) {
        Response r = given().contentType(JSON).accept(JSON)
                .body(String.format("{\"email\":\"%s\",\"password\":\"%s\"}", email, password))
                .post("/auth/login").andReturn();
        if (r.getStatusCode() == 200) {
            this.token = r.jsonPath().getString("token");
        }
        this.response = r;
        return r;
    }

    /** Ensures a logged-in user exists; registers a unique throw-away account when none is set yet. */
    public void ensureLoggedIn() {
        if (token == null) {
            String email = "player-" + UUID.randomUUID() + "@example.com";
            registerAccount(email, "喵喵科技", "s3cret!");
            bag.put("email", email);
        }
    }

    /** Credits the current wallet through the real {@code POST /wallet/topup} custom-amount path. */
    public Response topup(String currency, long amount) {
        Response r = http()
                .body(String.format("{\"currency\":\"%s\",\"amount\":%d}", currency, amount))
                .post("/wallet/topup").andReturn();
        this.response = r;
        return r;
    }

    // ── real DB reads (negative-state assertions & invariants) ────────
    public long count(String table) {
        Long n = jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
        return n == null ? 0L : n;
    }

    public long count(String table, String whereSql, Object... args) {
        Long n = jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE " + whereSql, Long.class, args);
        return n == null ? 0L : n;
    }

    public JdbcTemplate jdbc() {
        return jdbc;
    }

    /** Resolves the current logged-in account's DB id (by the registered email, else the latest row). */
    public long accountId() {
        String email = (String) bag.get("email");
        java.util.List<Long> ids;
        if (email != null) {
            ids = jdbc.query("SELECT id FROM member.accounts WHERE email = ?", (rs, n) -> rs.getLong(1), email);
        } else {
            ids = jdbc.query("SELECT id FROM member.accounts ORDER BY id DESC LIMIT 1", (rs, n) -> rs.getLong(1));
        }
        return ids.isEmpty() ? 0L : ids.get(0);
    }

    /** Resets RestAssured + per-scenario volatile state (called by {@link Hooks}). */
    public void resetHttp() {
        this.token = null;
        this.response = null;
        this.idempotencyKey = null;
        this.bag.clear();
    }

    static {
        // Fail fast on unexpected shapes rather than silently returning null bodies.
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }
}
