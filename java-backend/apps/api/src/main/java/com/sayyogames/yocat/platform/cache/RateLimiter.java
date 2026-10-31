package com.sayyogames.yocat.platform.cache;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

/**
 * Cross-cutting sliding-window rate limiter (EDD §11.3C 限流鍵設計；ARCH §6 "API 內 RateLimitFilter —
 * Redis 滑動視窗 Lua，原子"). Lives in the platform base so the platform {@code RateLimitFilter}
 * (EDD §4.5.1, Contract phase) and any endpoint can enforce per-IP / per-account budgets.
 *
 * <p>The window is evaluated atomically by the EDD §11.3C Lua script (loaded from
 * {@code redis/rate_limit_sliding_window.lua}): it trims entries older than the window
 * ({@code ZREMRANGEBYSCORE}), counts the window ({@code ZCARD}), and admits-and-records
 * ({@code ZADD} + {@code PEXPIRE}) only if under the limit — one round-trip, no race.</p>
 *
 * <h2>Failure mode — fail-open (EDD §11.2 / §8.3)</h2>
 * If Redis is unavailable or the {@code redis} circuit is open, {@link CacheGuard} returns the
 * fail-open fallback {@code true} (allow + WARN via the degraded metric), so a cache outage never
 * blocks legitimate traffic (EDD §8.3 "Open 時 … rate-limit fail-open").
 */
@Component
public class RateLimiter {

    private final StringRedisTemplate redis;
    private final CacheNamespace namespace;
    private final CacheGuard guard;
    private final CacheMetrics metrics;
    private final RedisScript<Long> slidingWindowScript;

    public RateLimiter(StringRedisTemplate redis,
                       CacheNamespace namespace,
                       CacheGuard guard,
                       CacheMetrics metrics) {
        this.redis = redis;
        this.namespace = namespace;
        this.guard = guard;
        this.metrics = metrics;
        this.slidingWindowScript = loadScript();
    }

    /**
     * Returns {@code true} if the request for {@code action} by {@code identifier} is within the
     * EDD §11.3C budget (and records it); {@code false} if the window limit is exceeded (caller
     * responds 429 + Retry-After, EDD §5.4). Fails open on a Redis outage.
     *
     * @param action     the rate-limited endpoint class (EDD §11.3C)
     * @param identifier the IP or account id dimension for this action
     */
    public boolean allow(RateLimitAction action, String identifier) {
        String key = namespace.rate(action.action(), identifier);
        String cache = "rate:" + action.action();
        long now = System.currentTimeMillis();
        return guard.call(cache,
                () -> {
                    Long result = redis.execute(
                            slidingWindowScript,
                            List.of(key),
                            Long.toString(now),
                            Long.toString(action.windowMillis()),
                            Integer.toString(action.limit()));
                    boolean allowed = result != null && result == 1L;
                    if (allowed) {
                        metrics.recordHit(cache);
                    } else {
                        metrics.recordMiss(cache);
                    }
                    return allowed;
                },
                () -> true); // fail-open: Redis down must not reject legitimate users (EDD §11.2)
    }

    private static RedisScript<Long> loadScript() {
        try {
            String lua = StreamUtils.copyToString(
                    new ClassPathResource("redis/rate_limit_sliding_window.lua").getInputStream(),
                    StandardCharsets.UTF_8);
            return new DefaultRedisScript<>(lua, Long.class);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to load redis/rate_limit_sliding_window.lua", e);
        }
    }
}
