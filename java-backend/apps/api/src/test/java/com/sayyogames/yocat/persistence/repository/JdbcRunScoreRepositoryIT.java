package com.sayyogames.yocat.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.sayyogames.yocat.leaderboard.domain.RunScore;
import com.sayyogames.yocat.leaderboard.domain.RunScoreRepository;
import com.sayyogames.yocat.persistence.support.AbstractRepositoryIT;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * RED integration test for the {@code JdbcRunScoreRepository} adapter behind {@link RunScoreRepository}
 * (O-04, EDD §7 Algorithm 2). Covers every port method against the real PostgreSQL 16 container —
 * {@code findTop} (score DESC, per mode) and {@code upsert} ({@code ON CONFLICT(run_id) DO UPDATE},
 * exercised via a rehydrated aggregate so no not-yet-defined domain constructor is needed). The adapter
 * bean does not exist until {@code persist-repo-impls}, so the suite is RED (Review #11).
 */
class JdbcRunScoreRepositoryIT extends AbstractRepositoryIT {

    @Autowired
    private RunScoreRepository runScoreRepository;

    @BeforeEach
    void seedLeaderboard() {
        long acct = seedAccount("lb-repo@example.com", "Leaderboard Owner");
        jdbc.update("INSERT INTO leaderboard.leaderboard_mode_catalog(mode, label) VALUES ('sandbox', 'Sandbox')");
        jdbc.update("INSERT INTO leaderboard.leaderboard(run_id, account_id, display_name, mode, score) "
                + "VALUES ('run_a', ?, 'Leaderboard Owner', 'sandbox', 300)", acct);
        jdbc.update("INSERT INTO leaderboard.leaderboard(run_id, account_id, display_name, mode, score) "
                + "VALUES ('run_b', ?, 'Leaderboard Owner', 'sandbox', 900)", acct);
        jdbc.update("INSERT INTO leaderboard.leaderboard(run_id, account_id, display_name, mode, score) "
                + "VALUES ('run_c', ?, 'Leaderboard Owner', 'sandbox', 600)", acct);
    }

    @Test
    @DisplayName("findTop returns the highest scores first, clamped to the limit")
    void findTopReturnsHighestScoresFirst() {
        List<RunScore> top = runScoreRepository.findTop("sandbox", 2);

        assertThat(top).hasSize(2);
        assertThat(top).extracting(RunScore::runId).containsExactly("run_b", "run_c");
    }

    @Test
    @DisplayName("upsert of an existing run_id updates in place rather than inserting a duplicate")
    void upsertKeepsSingleRowPerRunId() {
        RunScore existing = runScoreRepository.findTop("sandbox", 1).get(0);

        RunScore saved = runScoreRepository.upsert(existing);

        assertThat(saved.runId()).isEqualTo(existing.runId());
        Long rows = jdbc.queryForObject(
                "SELECT count(*) FROM leaderboard.leaderboard WHERE run_id = ?", Long.class, existing.runId());
        assertThat(rows).isEqualTo(1);
    }
}
