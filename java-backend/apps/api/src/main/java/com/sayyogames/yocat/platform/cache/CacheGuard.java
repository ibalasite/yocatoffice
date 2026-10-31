package com.sayyogames.yocat.platform.cache;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * Failure-mode boundary for every Redis operation (EDD §8.3 Circuit Breaker / §11.2 / §4.5.9;
 * template Review #7 + Anti-Fake 紅線 "cache 失敗導致 throw 中斷主流程").
 *
 * <p>Redis is a <em>degradable</em> dependency: a Redis outage must never surface as a 5xx on the
 * main request path. Every cache read/write is routed through this guard, which wraps the operation
 * in the shared {@code "redis"} Resilience4j circuit breaker (configured in {@code application.yml}
 * per EDD §8.3: 錯誤率 > 50% / 10s 窗 → Open；30s 後 Half-Open) and converts <em>any</em> Redis
 * failure — connection failure, timeout, or an open circuit — into the caller-supplied fallback:</p>
 * <ul>
 *   <li>session / leaderboard reads fall back to "miss" → caller 回源 DB (EDD §8.3 degradation).</li>
 *   <li>rate-limit checks fall back to "allow" → fail-open (EDD §11.2 "rate-limit fail-open").</li>
 *   <li>writes / evicts fall back to a no-op (the DB remains the source of truth).</li>
 * </ul>
 * Each degraded call is counted via {@link CacheMetrics#recordDegraded(String)} so the fallback
 * path is observable (EDD §10.1).
 */
@Component
public class CacheGuard {

    private final CircuitBreaker breaker;
    private final CacheMetrics metrics;

    public CacheGuard(CircuitBreakerRegistry registry, CacheMetrics metrics) {
        this.breaker = registry.circuitBreaker("redis");
        this.metrics = metrics;
    }

    /**
     * Executes {@code redisOp} under the {@code redis} circuit breaker, returning {@code fallback}
     * if Redis is unavailable, times out, or the circuit is open. Never propagates a Redis failure.
     */
    public <T> T call(String cache, Supplier<T> redisOp, Supplier<T> fallback) {
        try {
            return breaker.decorateSupplier(redisOp).get();
        } catch (RuntimeException e) {
            // Any Redis failure — connection failure (DataAccessException), timeout, or an open
            // circuit (CallNotPermittedException) — degrades to the fallback; never propagates.
            metrics.recordDegraded(cache);
            return fallback.get();
        }
    }

    /**
     * Executes a fire-and-forget Redis write/evict under the circuit breaker, swallowing any
     * failure (the DB is authoritative, so a lost cache write is tolerable — EDD §4.5.9).
     */
    public void run(String cache, Runnable redisOp) {
        call(cache, () -> {
            redisOp.run();
            return null;
        }, () -> null);
    }
}
