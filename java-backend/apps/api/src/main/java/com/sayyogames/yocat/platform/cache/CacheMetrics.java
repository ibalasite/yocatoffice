package com.sayyogames.yocat.platform.cache;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

/**
 * Cache hit / miss / degraded observability (EDD §10.1 Metrics; template Review #9 — "cache
 * hit/miss metric 暴露").
 *
 * <p>Emits Micrometer counters that surface on {@code /actuator/prometheus} (EDD §10.1):</p>
 * <ul>
 *   <li>{@code cache_requests_total{cache,result=hit|miss}} — effectiveness per cache class.</li>
 *   <li>{@code cache_degraded_total{cache}} — times a Redis failure forced the DB/fail-open
 *       fallback (EDD §8.3 circuit-breaker degradation), so the degraded path is observable rather
 *       than silent.</li>
 * </ul>
 * The {@code cache} tag is the logical cache name (e.g. {@code member:session},
 * {@code leaderboard:top}, {@code rate:login}).
 */
@Component
public class CacheMetrics {

    private final MeterRegistry registry;
    private final ConcurrentMap<String, Counter> hitCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> missCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> degradedCounters = new ConcurrentHashMap<>();

    public CacheMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordHit(String cache) {
        hitCounters.computeIfAbsent(cache, c ->
                Counter.builder("cache.requests")
                        .tag("cache", c).tag("result", "hit")
                        .description("Cache lookups that served from Redis")
                        .register(registry)).increment();
    }

    public void recordMiss(String cache) {
        missCounters.computeIfAbsent(cache, c ->
                Counter.builder("cache.requests")
                        .tag("cache", c).tag("result", "miss")
                        .description("Cache lookups that missed and fell through to the source")
                        .register(registry)).increment();
    }

    public void recordDegraded(String cache) {
        degradedCounters.computeIfAbsent(cache, c ->
                Counter.builder("cache.degraded")
                        .tag("cache", c)
                        .description("Cache operations degraded by a Redis failure (DB fallback / fail-open)")
                        .register(registry)).increment();
    }
}
