package com.sayyogames.yocat.leaderboard.domain;

import com.sayyogames.yocat.shared.AccountId;

/**
 * &lt;&lt;AggregateRoot&gt;&gt; — leaderboard BC run score (EDD §3.4 / §7 Algorithm 2, SCHEMA §3.6.1
 * {@code leaderboard.leaderboard}, PK {@code run_id}, upsert by {@code run_id}). Pure domain — no Spring,
 * no JDBC.
 *
 * <p>Fields mirror the SCHEMA columns {@code run_id / account_id / display_name / mode / score / grade}.
 * {@code display_name} is a deliberate denormalised snapshot of the member name at submission time (§4.2
 * Conformist). A score is rehydrated / constructed through {@link #of} and upserted by {@code run_id}.</p>
 */
public class RunScore {

    private String runId;
    private AccountId accountId;
    private String displayName;
    private String mode;
    private long score;
    private String grade;

    protected RunScore() {
    }

    private RunScore(String runId, AccountId accountId, String displayName, String mode, long score, String grade) {
        this.runId = runId;
        this.accountId = accountId;
        this.displayName = displayName;
        this.mode = mode;
        this.score = score;
        this.grade = grade;
    }

    /** Builds / rehydrates a run score (repository rehydration and SubmitScoreUseCase, SCHEMA §3.6.1). */
    public static RunScore of(String runId, AccountId accountId, String displayName, String mode, long score,
                              String grade) {
        return new RunScore(runId, accountId, displayName, mode, score, grade);
    }

    public String runId() {
        return runId;
    }

    public AccountId accountId() {
        return accountId;
    }

    /** Denormalised member display-name snapshot taken at submission time (§4.2). */
    public String displayName() {
        return displayName;
    }

    /** Leaderboard mode (default {@code sandbox}, CONSTANTS §9). */
    public String mode() {
        return mode;
    }

    public long score() {
        return score;
    }

    /** Optional computed grade; {@code null} when ungraded. */
    public String grade() {
        return grade;
    }
}
