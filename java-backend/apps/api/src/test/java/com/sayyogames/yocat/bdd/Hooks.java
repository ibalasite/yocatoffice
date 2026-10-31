package com.sayyogames.yocat.bdd;

import com.sayyogames.yocat.persistence.support.PostgresTestSupport;
import io.cucumber.java.Before;
import io.restassured.RestAssured;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Per-scenario setup hook: points RestAssured at the random embedded-server port, flushes Redis (rate-limit
 * counters) and truncates every bounded-context table so each scenario starts from a clean, order-independent
 * state (Review #9 Cleanup; test-plan §Test Isolation). Runs before every scenario's Background/Given.
 */
public class Hooks {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private StringRedisTemplate redis;

    @Value("${local.server.port}")
    private int port;

    @Before(order = 0)
    public void bindRestAssured() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
    }

    @Before(order = 5)
    public void flushRateLimitState() {
        // Rate-limit windows are per-IP; every scenario shares 127.0.0.1, so counters must not leak between
        // scenarios. Flushing the whole keyspace also resets any BC cache written during a scenario.
        try {
            redis.getConnectionFactory().getConnection().serverCommands().flushAll();
        } catch (RuntimeException ignored) {
            // A Redis outage degrades the limiter to fail-open; cleanup is best-effort and must not fail setup.
        }
    }

    @Before(order = 10)
    public void cleanDatabase() {
        jdbc.execute(PostgresTestSupport.TRUNCATE_ALL);
        seedReferenceCatalogs();
    }

    /**
     * Restores the bounded, seeded reference catalogs that the (deferred) V011 seed migration would supply but
     * that TRUNCATE wipes each scenario. Scenarios that query predefined packs (e.g. wallet top-up by pack id)
     * rely on this standard catalog existing even when they do not seed it themselves (CONSTANTS §7).
     */
    private void seedReferenceCatalogs() {
        jdbc.update("INSERT INTO deposit.deposit_pack(pack_id, currency, amount, label, bonus_pct, is_active) "
                + "VALUES ('g_small','gems',60,'小寶石包',NULL,TRUE), "
                + "('g_mid','gems',330,'中寶石包',10,TRUE), "
                + "('g_big','gems',700,'大寶石包',16,TRUE), "
                + "('cap_1000','cash',1000,'資本儲值包',NULL,TRUE) "
                + "ON CONFLICT (pack_id) DO NOTHING");
    }
}
