package com.sayyogames.yocat.leaderboard.domain;

import java.util.List;

/**
 * &lt;&lt;Repository&gt;&gt; port for the {@link RunScore} aggregate (leaderboard BC).
 *
 * <p>Domain-owned interface; infrastructure adapter injected via Spring DI (DIP).</p>
 */
public interface RunScoreRepository {

    /**
     * Top scores ordered by {@code score DESC} (SCHEMA {@code idx_lb_mode_score}). An empty {@code mode}
     * means all modes; {@code limit} is clamped to {@code [1,100]} by the caller (EDD §7 algorithm 2 / API §330).
     * Caller: GetLeaderboardUseCase (GET /leaderboard).
     */
    List<RunScore> findTop(String mode, int limit);

    /**
     * Upsert the run score keyed by {@code run_id} ({@code ON CONFLICT(run_id) DO UPDATE} — latest score wins).
     * Caller: SubmitScoreUseCase (POST /leaderboard, EDD §7).
     */
    RunScore upsert(RunScore runScore);
}
