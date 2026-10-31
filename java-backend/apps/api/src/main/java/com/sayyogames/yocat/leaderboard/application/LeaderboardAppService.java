package com.sayyogames.yocat.leaderboard.application;

import com.sayyogames.yocat.leaderboard.domain.RunScore;
import com.sayyogames.yocat.leaderboard.domain.RunScoreRepository;
import com.sayyogames.yocat.leaderboard.domain.RunScoreSubmitted;
import com.sayyogames.yocat.shared.AccountId;
import com.sayyogames.yocat.shared.DomainEventPublisher;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * &lt;&lt;ApplicationService&gt;&gt; leaderboard BC query + submit (EDD §7 Algorithm 2 / API §3 leaderboard:
 * {@code GET /leaderboard} public, {@code POST /leaderboard} authenticated). Submit is a server-authoritative
 * upsert keyed by {@code run_id} (latest score wins, one row per run) attributed with the member's display-name
 * snapshot (§4.2), publishing {@code RunScoreSubmitted} in the same transaction (Outbox). A blank {@code runId}
 * is rejected (400 {@code "缺 runId"}).
 */
@Service
public class LeaderboardAppService {

    private static final String DEFAULT_MODE = "sandbox";

    private final RunScoreRepository runScoreRepo;
    private final DomainEventPublisher publisher;

    public LeaderboardAppService(RunScoreRepository runScoreRepo, DomainEventPublisher publisher) {
        this.runScoreRepo = runScoreRepo;
        this.publisher = publisher;
    }

    @Transactional(readOnly = true)
    public List<RunScore> topN(String mode, int limit) {
        return runScoreRepo.findTop(mode, limit);
    }

    @Transactional
    public void submit(long accountId, String displayName, String runId, String mode, long score, String grade) {
        if (runId == null || runId.isBlank()) {
            throw new IllegalArgumentException("缺 runId");
        }
        String resolvedMode = (mode == null || mode.isBlank()) ? DEFAULT_MODE : mode;
        AccountId acct = AccountId.of(accountId);
        runScoreRepo.upsert(RunScore.of(runId, acct, displayName, resolvedMode, score, grade));
        publisher.publish(new RunScoreSubmitted(runId, acct, resolvedMode, score));
    }
}
