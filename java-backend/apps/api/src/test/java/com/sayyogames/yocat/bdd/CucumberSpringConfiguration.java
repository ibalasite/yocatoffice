package com.sayyogames.yocat.bdd;

import com.sayyogames.yocat.YocatOfficeBackendApplication;
import com.sayyogames.yocat.persistence.support.PostgresTestSupport;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static com.sayyogames.yocat.persistence.support.PostgresTestSupport.POSTGRES;

/**
 * Cucumber ↔ Spring bridge: boots the <b>real</b> {@link YocatOfficeBackendApplication} context once
 * for the whole BDD suite, with a random-port embedded web server so the {@code @api} scenarios can
 * drive the actual REST controllers over HTTP (test-plan §BDD; Review #2 "When 觸發真實 Controller",
 * no mocked business logic).
 *
 * <p>Persistence runs against the shared Testcontainers PostgreSQL 16 ({@link PostgresTestSupport}) with
 * the real {@code migrations/} DDL applied — the same container the Persist-phase {@code *IT} suite uses
 * (Review #3 "Background 觸發真實 setup / Testcontainers seeding"; Review #9 per-scenario cleanup via
 * {@link Hooks}).</p>
 *
 * <p>A real Redis 7.2 container backs the platform rate limiter so the 429 sliding-window scenarios
 * (EDD §11.3C) exercise the production path rather than the fail-open degraded path. The manual
 * {@code platform.cache.RedisConfig} owns the connection factory, so Spring Boot's
 * {@code RedisAutoConfiguration} stays excluded (no duplicate factory); only {@code spring.data.redis.host/port}
 * are pointed at the container. {@link Hooks} flushes Redis before each scenario so per-IP limit counters never
 * leak across scenarios.</p>
 */
@CucumberContextConfiguration
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
public class CucumberSpringConfiguration {

    /** Real Redis 7.2 for the EDD §11.3C sliding-window rate limiter (started once per JVM, reused). */
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.2")).withExposedPorts(6379);

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
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
}
