package com.sayyogames.yocat.persistence.support;

import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base for DB-contract integration tests that assert the real PostgreSQL 16 schema behaviour
 * (SCHEMA §Constraints / §Indexes / §ER Diagram) directly against the migrated container, with no
 * Spring application context. These verify that the database itself enforces PK / FK / UNIQUE / CHECK
 * and that {@code SELECT ... FOR UPDATE} serialises concurrent writers (test-plan §3.2 Database Layer,
 * "驗證 DB 真的拒絕" — Review #3 / #4).
 *
 * <p>Uses a plain {@link DriverManagerDataSource} so every {@link DataSource#getConnection()} returns a
 * distinct physical connection — required for the multi-connection row-lock concurrency vectors
 * (CONS-01 / CONS-02, test-plan §3.5).</p>
 */
@Testcontainers
public abstract class AbstractSchemaIT {

    protected static DataSource dataSource;
    protected static JdbcTemplate jdbc;

    @BeforeAll
    static void startRealDatabase() {
        PostgresTestSupport.ensureStartedAndMigrated();
        DriverManagerDataSource ds = new DriverManagerDataSource(
                PostgresTestSupport.POSTGRES.getJdbcUrl(),
                PostgresTestSupport.POSTGRES.getUsername(),
                PostgresTestSupport.POSTGRES.getPassword());
        ds.setDriverClassName("org.postgresql.Driver");
        dataSource = ds;
        jdbc = new JdbcTemplate(ds);
    }

    @BeforeEach
    void resetDatabase() {
        jdbc.execute(PostgresTestSupport.TRUNCATE_ALL);
    }

    /** Inserts an active member account and returns its generated {@code BIGSERIAL} id. */
    protected long insertAccount(String email, String displayName) {
        return jdbc.queryForObject(
                "INSERT INTO member.accounts(email, display_name, pass_hash) VALUES (?, ?, ?) RETURNING id",
                Long.class, email, displayName, "salt$hash");
    }
}
