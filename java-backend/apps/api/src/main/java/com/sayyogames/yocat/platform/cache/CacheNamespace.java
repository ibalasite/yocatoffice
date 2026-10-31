package com.sayyogames.yocat.platform.cache;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Centralized Redis key builder enforcing the single key-namespace contract (EDD §21.3 Redis Key
 * Namespace Table — HC-4 強制; EDD §11.3A 鍵命名規範; ARCH §7 Redis Key Namespace).
 *
 * <p>Key format is {@code {env}:{service}:{entity}:{id}} (EDD §11.3A), where {@code env ∈
 * prod/staging/dev/local}, lower-case, no spaces. Every BC prefix is defined here and nowhere else,
 * so no two BCs can collide on a prefix (EDD §21.3 "禁止各 BC 自行決定（防 cache 互污染）"). BCs
 * that run strong-consistency against the DB (wallet / deposit / entitlement / record / game-save)
 * have no Redis key and therefore no builder method here (EDD §21.3).</p>
 *
 * <h2>Cluster hash tags (ARCH §7 隔離強制策略)</h2>
 * When {@code yocat.cache.redis.cluster-hash-tag=true} (Redis Cluster deployment), the service
 * segment is wrapped as a hash tag {@code {service}} so all keys of one BC hash to the same slot,
 * keeping BC key-spaces co-located and multi-key ops valid. The default (standalone / Sentinel, as
 * used locally and in prod per EDD §3.5) is {@code false}: a single logical key-space needs no tag.
 */
@Component
public class CacheNamespace {

    private final String env;
    private final boolean clusterHashTag;

    public CacheNamespace(
            @Value("${yocat.cache.env:local}") String env,
            @Value("${yocat.cache.redis.cluster-hash-tag:false}") boolean clusterHashTag) {
        this.env = env;
        this.clusterHashTag = clusterHashTag;
    }

    /** {@code {env}:member:session:{token}} — session 驗證快取 (EDD §21.3 / §11.3A, TTL 1800s). */
    public String memberSession(String token) {
        return env + ":" + service("member") + ":session:" + token;
    }

    /** {@code {env}:leaderboard:top:{mode}:{limit}} — Top N 查詢快取 (EDD §21.3 / §11.3A, TTL 30s). */
    public String leaderboardTop(String mode, int limit) {
        return env + ":" + service("leaderboard") + ":top:" + mode + ":" + limit;
    }

    /** Distributed rebuild lock for a leaderboard page (EDD §11.3E 擊穿 "SET lock NX EX 5"). */
    public String leaderboardRebuildLock(String mode, int limit) {
        return env + ":" + service("leaderboard") + ":top-lock:" + mode + ":" + limit;
    }

    /**
     * {@code {env}:rate:{action}:{identifier}} — 滑動視窗限流 (EDD §21.3 平台底座橫切 / §11.3C).
     * {@code action ∈ login/register/wallet/buy/lb}; {@code identifier} is an IP or account id.
     */
    public String rate(String action, String identifier) {
        return env + ":" + service("rate") + ":" + action + ":" + identifier;
    }

    /** Configured cache environment prefix ({@code prod/staging/dev/local}). */
    public String env() {
        return env;
    }

    private String service(String bc) {
        return clusterHashTag ? "{" + bc + "}" : bc;
    }
}
