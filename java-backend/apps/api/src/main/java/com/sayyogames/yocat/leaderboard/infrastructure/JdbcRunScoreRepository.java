package com.sayyogames.yocat.leaderboard.infrastructure;

import com.sayyogames.yocat.leaderboard.domain.RunScore;
import com.sayyogames.yocat.leaderboard.domain.RunScoreRepository;
import com.sayyogames.yocat.shared.AccountId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Infrastructure JDBC adapter for the {@link RunScoreRepository} port (O-04, EDD §7 Algorithm 2; §2 DIP).
 * Implements the domain-owned port against {@code leaderboard.leaderboard} on the real PostgreSQL 16 schema
 * (SCHEMA §3.6.1, PK {@code run_id}, {@code idx_lb_mode_score}).
 *
 * <p>{@link #findTop} returns the highest scores first ({@code score DESC}) for a mode (an empty mode spans all
 * modes), clamped to {@code [1,100]}. {@link #upsert} persists by {@code run_id} with
 * {@code ON CONFLICT(run_id) DO UPDATE} so the latest submission wins without creating a duplicate row. All
 * statements are parameterised.</p>
 */
@Repository
public class JdbcRunScoreRepository implements RunScoreRepository {

    private static final int MAX_LIMIT = 100;

    private static final RowMapper<RunScore> RUN_SCORE_MAPPER = (rs, rowNum) -> RunScore.of(
            rs.getString("run_id"),
            AccountId.of(rs.getLong("account_id")),
            rs.getString("display_name"),
            rs.getString("mode"),
            rs.getLong("score"),
            rs.getString("grade"));

    private final JdbcTemplate jdbc;

    public JdbcRunScoreRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<RunScore> findTop(String mode, int limit) {
        int clamped = Math.max(1, Math.min(limit, MAX_LIMIT));
        StringBuilder sql = new StringBuilder(
                "SELECT run_id, account_id, display_name, mode, score, grade FROM leaderboard.leaderboard");
        List<Object> args = new ArrayList<>();
        if (mode != null && !mode.isBlank()) {
            sql.append(" WHERE mode = ?");
            args.add(mode);
        }
        sql.append(" ORDER BY score DESC, run_id LIMIT ?");
        args.add(clamped);
        return jdbc.query(sql.toString(), RUN_SCORE_MAPPER, args.toArray());
    }

    @Override
    public RunScore upsert(RunScore runScore) {
        return jdbc.queryForObject(
                "INSERT INTO leaderboard.leaderboard(run_id, account_id, display_name, mode, score, grade) "
                        + "VALUES (?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (run_id) DO UPDATE SET account_id = EXCLUDED.account_id, "
                        + "display_name = EXCLUDED.display_name, mode = EXCLUDED.mode, score = EXCLUDED.score, "
                        + "grade = EXCLUDED.grade, updated_at = now() "
                        + "RETURNING run_id, account_id, display_name, mode, score, grade",
                RUN_SCORE_MAPPER,
                runScore.runId(), runScore.accountId().value(), runScore.displayName(),
                runScore.mode(), runScore.score(), runScore.grade());
    }
}
