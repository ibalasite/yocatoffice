package com.sayyogames.yocat.member.infrastructure;

import com.sayyogames.yocat.platform.cache.CacheGuard;
import com.sayyogames.yocat.platform.cache.CacheMetrics;
import com.sayyogames.yocat.platform.cache.CacheNamespace;
import com.sayyogames.yocat.platform.cache.CacheTtl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * member BC session cache — the Redis half of the opaque-session store (EDD §4.5.4 "SessionStore
 * (Redis)"; ADR-003 Opaque Session Token (Redis+DB); §4.5.9 Cache Contract; §21.3 key
 * {@code member:session:{token}}).
 *
 * <p><b>BC isolation (EDD §21.3 / ARCH §7):</b> this component belongs to the member module and
 * must not be imported by other BCs; cross-BC reads go through the member Public API, never this
 * cache. All Redis access is funnelled through {@link CacheGuard}, so a Redis outage degrades to a
 * cache miss and the caller (MemberService.verifySession, EDD §4.2) 回源 DB — never a 5xx.</p>
 *
 * <h2>Strategy (EDD §4.5.9 Cache Contract row {@code prod:member:session:{token}})</h2>
 * <ul>
 *   <li><b>write-through</b>, stale-read tolerance 0 — {@link #put} on a DB-authoritative read,
 *       {@link #evict} synchronously on logout / revocation so a logged-out token is never served
 *       from cache.</li>
 *   <li><b>TTL</b> {@link CacheTtl#SESSION} (1800s) with ±10% jitter (EDD §11.3A), the hot-cache
 *       TTL over the 30-day DB-authoritative session.</li>
 *   <li><b>penetration guard</b> (EDD §11.3E 穿透): {@link #cacheAbsent} negative-caches an unknown
 *       token as an empty value for {@link CacheTtl#NULL_VALUE} (30s) so repeated bogus tokens do
 *       not stampede the DB.</li>
 * </ul>
 */
@Component
public class SessionCache {

    /** EDD §11.3E 穿透對策：空值標記 ("SET key \"\" EX 30"). */
    private static final String NEGATIVE = "";

    private final StringRedisTemplate redis;
    private final CacheNamespace namespace;
    private final CacheGuard guard;
    private final CacheMetrics metrics;

    public SessionCache(StringRedisTemplate redis,
                        CacheNamespace namespace,
                        CacheGuard guard,
                        CacheMetrics metrics) {
        this.redis = redis;
        this.namespace = namespace;
        this.guard = guard;
        this.metrics = metrics;
    }

    /** Lookup outcome: a cache HIT (session JSON), a NEGATIVE hit (token known-absent), or a MISS. */
    public enum Status { HIT, NEGATIVE, MISS }

    /** Immutable session-cache lookup result. */
    public record Lookup(Status status, String value) {
        public static Lookup hit(String value) {
            return new Lookup(Status.HIT, value);
        }

        public static final Lookup NEGATIVE_RESULT = new Lookup(Status.NEGATIVE, null);
        public static final Lookup MISS_RESULT = new Lookup(Status.MISS, null);
    }

    /**
     * Reads the cached session for {@code token}. Returns {@link Status#HIT} with the stored session
     * value, {@link Status#NEGATIVE} if the token was negative-cached as absent, or
     * {@link Status#MISS} on a cache miss or Redis degradation (caller 回源 DB).
     */
    public Lookup find(String token) {
        String cache = "member:session";
        String key = namespace.memberSession(token);
        Lookup result = guard.call(cache,
                () -> {
                    String raw = redis.opsForValue().get(key);
                    if (raw == null) {
                        return Lookup.MISS_RESULT;
                    }
                    return NEGATIVE.equals(raw) ? Lookup.NEGATIVE_RESULT : Lookup.hit(raw);
                },
                () -> Lookup.MISS_RESULT);
        if (result.status() == Status.HIT || result.status() == Status.NEGATIVE) {
            metrics.recordHit(cache);
        } else {
            metrics.recordMiss(cache);
        }
        return result;
    }

    /** write-through: cache a DB-authoritative session value, TTL 1800s ±10% jitter (EDD §11.3A). */
    public void put(String token, String sessionValue) {
        String key = namespace.memberSession(token);
        guard.run("member:session",
                () -> redis.opsForValue().set(key, sessionValue, CacheTtl.withJitter(CacheTtl.SESSION)));
    }

    /** 穿透對策：negative-cache an unknown token as empty for 30s (EDD §11.3E). */
    public void cacheAbsent(String token) {
        String key = namespace.memberSession(token);
        guard.run("member:session",
                () -> redis.opsForValue().set(key, NEGATIVE, CacheTtl.NULL_VALUE));
    }

    /** write-through invalidation on logout / revocation — stale tolerance 0 (EDD §4.5.9). */
    public void evict(String token) {
        String key = namespace.memberSession(token);
        guard.run("member:session", () -> redis.delete(key));
    }
}
