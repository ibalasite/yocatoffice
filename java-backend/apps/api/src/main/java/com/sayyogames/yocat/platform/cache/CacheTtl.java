package com.sayyogames.yocat.platform.cache;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Canonical cache TTLs and the anti-avalanche jitter formula (EDD §11.3A 鍵命名規範與清單 /
 * §11.3E 快取失效對策).
 *
 * <p>Each cache class has one explicit TTL — no un-bounded (TTL-less) keys exist (EDD §21.3 "每 key
 * pattern 有明確 TTL"; template Review #2). Values are grounded verbatim in EDD §11.3A / §11.3E:</p>
 * <ul>
 *   <li>{@link #SESSION} 1800s — session 驗證熱快取 (DB authoritative 30d).</li>
 *   <li>{@link #LEADERBOARD} 30s — Top N 查詢快取 (stale-read ≤30s 可接受).</li>
 *   <li>{@link #RATE_WINDOW} 60s — 滑動視窗限流視窗 (PEXPIRE in §11.3C Lua).</li>
 *   <li>{@link #NULL_VALUE} 30s — 穿透對策，空值快取 ("SET key \"\" EX 30").</li>
 *   <li>{@link #REBUILD_LOCK} 5s — 擊穿對策，分散鎖 ("SET lock NX EX 5").</li>
 * </ul>
 *
 * <h2>TTL Jitter (EDD §11.3A)</h2>
 * {@code actual_ttl = base_ttl × rand(0.9, 1.1)} — applied to the batch-expiring session and
 * leaderboard caches to defend against cache avalanche (§11.3E 雪崩). Fixed-window keys (rate limit)
 * are not jittered (EDD §11.3A jitter column = 無).
 */
public final class CacheTtl {

    /** session 驗證快取 (EDD §11.3A / §4.5.9 Cache Contract). */
    public static final Duration SESSION = Duration.ofSeconds(1800);

    /** Top N 查詢快取 (EDD §11.3A). */
    public static final Duration LEADERBOARD = Duration.ofSeconds(30);

    /** 滑動視窗限流視窗長度 (EDD §11.3A / §11.3C). */
    public static final Duration RATE_WINDOW = Duration.ofSeconds(60);

    /** 穿透對策：不存在 token 的空值快取 (EDD §11.3E). */
    public static final Duration NULL_VALUE = Duration.ofSeconds(30);

    /** 擊穿對策：熱門頁重建分散鎖 (EDD §11.3E). */
    public static final Duration REBUILD_LOCK = Duration.ofSeconds(5);

    private static final double JITTER_LOW = 0.9;
    private static final double JITTER_HIGH = 1.1;

    private CacheTtl() {
    }

    /**
     * Applies the EDD §11.3A anti-avalanche jitter: {@code base × rand(0.9, 1.1)}.
     * Used for the session and leaderboard caches on {@code SET ... EX}.
     */
    public static Duration withJitter(Duration base) {
        double factor = ThreadLocalRandom.current().nextDouble(JITTER_LOW, JITTER_HIGH);
        long millis = Math.round(base.toMillis() * factor);
        return Duration.ofMillis(millis);
    }
}
