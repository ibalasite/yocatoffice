package com.sayyogames.yocat.leaderboard.domain;

import com.sayyogames.yocat.shared.AccountId;
import com.sayyogames.yocat.shared.DomainEvent;

import java.time.Instant;
import java.util.Objects;

/**
 * &lt;&lt;DomainEvent&gt;&gt; — owned by the leaderboard BC (EDD §4.6.1 {@code leaderboard.run.submitted} v1).
 * Published after an upsert-by-{@code run_id} (EDD §4.7 Algorithm 2 submit); BC-internal, triggers
 * cache invalidation (EDD §4.6.1 Consumer BC(s)). Pure domain — no Spring, no JDBC.
 *
 * @param runId      the submitted run's id (upsert key)
 * @param accountId  the submitting account's identity
 * @param mode       the game mode of the run
 * @param score      the submitted score
 * @param occurredAt the instant the submission committed
 */
public record RunScoreSubmitted(
        String runId,
        AccountId accountId,
        String mode,
        long score,
        Instant occurredAt) implements DomainEvent {

    public RunScoreSubmitted {
        Objects.requireNonNull(runId, "runId 不可為 null");
        Objects.requireNonNull(accountId, "accountId 不可為 null");
        Objects.requireNonNull(mode, "mode 不可為 null");
        Objects.requireNonNull(occurredAt, "occurredAt 不可為 null");
    }

    /** Raises the event stamped at the current instant. */
    public RunScoreSubmitted(String runId, AccountId accountId, String mode, long score) {
        this(runId, accountId, mode, score, Instant.now());
    }
}
