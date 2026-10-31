package com.sayyogames.yocat.leaderboard.infrastructure;

import com.sayyogames.yocat.leaderboard.domain.RunScoreSubmitted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * leaderboard BC internal consumer of {@code RunScoreSubmitted} (EDD §4.6.1 — the event "triggers cache
 * invalidation" downstream). Records the submission for observability (EDD §10.1); the Top-N cache is
 * short-TTL/guarded (persist-cache-config), so a structured-log sink is the grounded downstream reaction here.
 * Being an {@link ApplicationModuleListener}, it routes the event through the transactional Outbox /
 * event-publication registry (at-least-once, module-decoupled delivery).
 */
@Component
public class LeaderboardEventListener {

    private static final Logger log = LoggerFactory.getLogger(LeaderboardEventListener.class);

    @ApplicationModuleListener
    public void onRunScoreSubmitted(RunScoreSubmitted event) {
        log.info("leaderboard run submitted: runId={} mode={} score={} account={}",
                event.runId(), event.mode(), event.score(), event.accountId().value());
    }
}
