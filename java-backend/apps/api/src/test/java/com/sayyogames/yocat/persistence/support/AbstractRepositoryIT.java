package com.sayyogames.yocat.persistence.support;

import static com.sayyogames.yocat.persistence.support.PostgresTestSupport.POSTGRES;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base for repository-adapter integration tests (O-04 Jdbc*Repository behind the O-03 domain ports).
 * Boots the real Spring Data JDBC stack against the shared Testcontainers PostgreSQL 16 (EDD §3.3,
 * test-plan §3.1 Infrastructure layer — "JdbcWalletRepositoryIT 使用 Testcontainers 啟動真實 PostgreSQL 16").
 *
 * <p><b>TDD RED:</b> the concrete {@code Jdbc*Repository} adapter beans do not exist yet (they are the
 * next GREEN sub-step {@code persist-repo-impls}). Each concrete {@code *IT} {@code @Autowired}s its
 * domain repository port, so the Spring test instance fails to resolve the port bean and the suite is
 * RED until the adapters are implemented (Review #11). The schema is already present because
 * {@code migrations/} is applied to the container before the context starts.</p>
 *
 * <p>Redis is excluded from autoconfiguration because this slice exercises only the relational
 * persistence port; the Redis cache container is wired by {@code persist-cache-config}.</p>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
                "spring.flyway.enabled=false",
                "spring.modulith.events.jdbc.schema-initialization.enabled=false"
        })
@Testcontainers
public abstract class AbstractRepositoryIT {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        PostgresTestSupport.ensureStartedAndMigrated();
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired
    protected JdbcTemplate jdbc;

    @BeforeEach
    void resetDatabase() {
        jdbc.execute(PostgresTestSupport.TRUNCATE_ALL);
    }

    /** Inserts an active member account row directly and returns its generated id (test fixture seed). */
    protected long seedAccount(String email, String displayName) {
        return jdbc.queryForObject(
                "INSERT INTO member.accounts(email, display_name, pass_hash) VALUES (?, ?, ?) RETURNING id",
                Long.class, email, displayName, "salt$hash");
    }
}
