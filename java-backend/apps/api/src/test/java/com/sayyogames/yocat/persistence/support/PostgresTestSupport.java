package com.sayyogames.yocat.persistence.support;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * Singleton Testcontainers PostgreSQL 16 holder for the Persist-phase integration suite
 * (test-plan §3.2 Database Layer — "Testcontainers（PostgreSQL 16，實際 DB）"; EDD §3.3 Tech Stack
 * {@code db_type=postgres}; ARCH §Deployment Topology).
 *
 * <p>A single real {@code postgres:16} container is started once per JVM and shared across every
 * {@code *IT} class (started lazily, reused, torn down by the Testcontainers Ryuk reaper). The real
 * Flyway DDL under {@code migrations/} (V001–V012) is applied verbatim through the container's own
 * {@code psql} client so that dollar-quoted PL/pgSQL bodies (triggers, {@code platform.set_updated_at})
 * execute exactly as production Flyway runs them — no in-memory DB, no mock, no hand-split SQL.</p>
 */
public final class PostgresTestSupport {

    /** Shared real PostgreSQL 16 container (EDD §3.3 / SCHEMA §ER Diagram source of truth). */
    public static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"))
                    .withDatabaseName("yocat")
                    .withUsername("yocat")
                    .withPassword("yocat");

    /**
     * FK-safe per-test reset of every BC table (test-plan §1.2 Test Isolation / Review #12 Cleanup).
     * {@code CASCADE} + {@code RESTART IDENTITY} guarantees order-independent, deterministic tests.
     */
    public static final String TRUNCATE_ALL = "TRUNCATE "
            + "member.password_history, member.account_deletion_requests, member.login_attempts, "
            + "member.sessions, member.accounts, "
            + "wallet.wallet_inbound_events, wallet.wallet_balance_snapshots, wallet.wallet_ledger, wallet.wallets, "
            + "deposit.deposit_payment_webhook, deposit.deposit_idempotency, deposit.deposit_order, deposit.deposit_pack, "
            + "entitlement.entitlement_grant_log, entitlement.entitlements, entitlement.entitlement_catalog, "
            + "record.records, record.record_type_catalog, "
            + "leaderboard.leaderboard_snapshot, leaderboard.leaderboard, leaderboard.leaderboard_mode_catalog, "
            + "game_save.save_write_log, game_save.saves, "
            + "modulith.event_dead_letter, modulith.event_publication_archive, modulith.event_publication "
            + "RESTART IDENTITY CASCADE";

    private static volatile boolean migrated = false;

    private PostgresTestSupport() {
    }

    /** Starts the shared container (once) and applies the real {@code migrations/} DDL (once). */
    public static synchronized void ensureStartedAndMigrated() {
        if (!POSTGRES.isRunning()) {
            POSTGRES.start();
        }
        if (!migrated) {
            applyMigrations();
            migrated = true;
        }
    }

    private static void applyMigrations() {
        Path migrationsDir = locateMigrationsDir();
        List<Path> scripts;
        try (Stream<Path> stream = Files.list(migrationsDir)) {
            scripts = stream
                    .filter(p -> p.getFileName().toString().matches("V\\d+__.*\\.sql"))
                    .sorted()
                    .toList();
        } catch (Exception e) {
            throw new IllegalStateException("Cannot list migrations directory: " + migrationsDir, e);
        }
        if (scripts.isEmpty()) {
            throw new IllegalStateException("No Flyway migrations found under " + migrationsDir);
        }
        for (Path script : scripts) {
            String name = script.getFileName().toString();
            POSTGRES.copyFileToContainer(MountableFile.forHostPath(script.toString()), "/migrations/" + name);
            ExecResult result;
            try {
                result = POSTGRES.execInContainer(
                        "bash", "-lc",
                        "PGPASSWORD='" + POSTGRES.getPassword() + "' psql -v ON_ERROR_STOP=1 -U "
                                + POSTGRES.getUsername() + " -h 127.0.0.1 -p 5432 -d "
                                + POSTGRES.getDatabaseName() + " -f /migrations/" + name);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to execute migration " + name, e);
            }
            if (result.getExitCode() != 0) {
                throw new IllegalStateException(
                        "Migration " + name + " failed (exit " + result.getExitCode() + "):\n"
                                + result.getStderr());
            }
        }
    }

    /** Walks up from the JVM working directory to find the single {@code migrations/} Flyway source. */
    private static Path locateMigrationsDir() {
        Path cursor = new File(System.getProperty("user.dir")).toPath().toAbsolutePath();
        for (int depth = 0; depth < 6 && cursor != null; depth++) {
            Path candidate = cursor.resolve("migrations");
            if (Files.isDirectory(candidate)
                    && Files.exists(candidate.resolve("V001__create_schemas_and_enums.sql"))) {
                return candidate;
            }
            cursor = cursor.getParent();
        }
        throw new IllegalStateException(
                "Could not locate migrations/ directory from user.dir=" + System.getProperty("user.dir"));
    }
}
