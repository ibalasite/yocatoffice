package com.sayyogames.yocat.leaderboard.infrastructure;

import com.sayyogames.yocat.platform.cache.CacheGuard;
import com.sayyogames.yocat.platform.cache.CacheMetrics;
import com.sayyogames.yocat.platform.cache.CacheNamespace;
import com.sayyogames.yocat.platform.cache.CacheTtl;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * leaderboard BC Top-N page cache (EDD §4.5.9 Cache Contract row
 * {@code prod:leaderboard:top:{mode}:{limit}}; §21.3 key {@code leaderboard:top:{mode}:{limit}};
 * §11.3A/B/E). Caches the rendered Top-N JSON page for the {@code GET /leaderboard} query
 * (EDD §4.7 Alg 2 LeaderboardService.topN).
 *
 * <p><b>BC isolation (EDD §21.3 / ARCH §7):</b> member of the leaderboard module; not imported by
 * other BCs. All Redis access is funnelled through {@link CacheGuard}, so a Redis outage degrades to
 * a miss and the caller queries the DB directly via {@code idx_lb_mode_score} (EDD §8.3) — never a
 * 5xx.</p>
 *
 * <h2>Strategy (EDD §4.5.9 Cache Contract)</h2>
 * <ul>
 *   <li><b>write-behind</b>, stale-read tolerance ≤30s — the 30s TTL bounds staleness; the write
 *       path ({@code POST /leaderboard} upsert → {@code RunScoreSubmitted}, EDD §21.2) additionally
 *       {@link #invalidate}s the affected page so a new high score is reflected promptly.</li>
 *   <li><b>TTL</b> {@link CacheTtl#LEADERBOARD} (30s) with ±10% jitter (EDD §11.3A).</li>
 *   <li><b>breakdown guard</b> (EDD §11.3E 擊穿): {@link #acquireRebuildLock} is a {@code SET NX EX
 *       5} mutex so only one request rebuilds a hot expired page while others serve/await.</li>
 * </ul>
 */
@Component
public class LeaderboardCache {

    private final StringRedisTemplate redis;
    private final CacheNamespace namespace;
    private final CacheGuard guard;
    private final CacheMetrics metrics;

    public LeaderboardCache(StringRedisTemplate redis,
                            CacheNamespace namespace,
                            CacheGuard guard,
                            CacheMetrics metrics) {
        this.redis = redis;
        this.namespace = namespace;
        this.guard = guard;
        this.metrics = metrics;
    }

    /**
     * Returns the cached Top-N JSON page for {@code (mode, limit)}, or {@link Optional#empty()} on a
     * cache miss or Redis degradation (caller 回源 DB via {@code idx_lb_mode_score}).
     */
    public Optional<String> getTop(String mode, int limit) {
        String cache = "leaderboard:top";
        String key = namespace.leaderboardTop(mode, limit);
        Optional<String> result = guard.call(cache,
                () -> Optional.ofNullable(redis.opsForValue().get(key)),
                Optional::empty);
        if (result.isPresent()) {
            metrics.recordHit(cache);
        } else {
            metrics.recordMiss(cache);
        }
        return result;
    }

    /** write-behind: cache a rendered Top-N page, TTL 30s ±10% jitter (EDD §11.3A). */
    public void putTop(String mode, int limit, String pageJson) {
        String key = namespace.leaderboardTop(mode, limit);
        guard.run("leaderboard:top",
                () -> redis.opsForValue().set(key, pageJson, CacheTtl.withJitter(CacheTtl.LEADERBOARD)));
    }

    /** Invalidate a specific cached page on the write path (EDD §21.2 RunScoreSubmitted → cache 失效). */
    public void invalidate(String mode, int limit) {
        String key = namespace.leaderboardTop(mode, limit);
        guard.run("leaderboard:top", () -> redis.delete(key));
    }

    /**
     * 擊穿對策：try to acquire the rebuild mutex for a hot expired page ({@code SET lock NX EX 5},
     * EDD §11.3E). Returns {@code true} to the single winner that should rebuild from the DB; losers
     * serve stale / retry. On Redis degradation returns {@code true} (rebuild proceeds against DB).
     */
    public boolean acquireRebuildLock(String mode, int limit) {
        String key = namespace.leaderboardRebuildLock(mode, limit);
        return guard.call("leaderboard:top",
                () -> Boolean.TRUE.equals(
                        redis.opsForValue().setIfAbsent(key, "1", CacheTtl.REBUILD_LOCK)),
                () -> true);
    }

    /** Release the rebuild mutex after the page is refreshed. */
    public void releaseRebuildLock(String mode, int limit) {
        String key = namespace.leaderboardRebuildLock(mode, limit);
        guard.run("leaderboard:top", () -> redis.delete(key));
    }
}
