package com.sayyogames.yocat.platform.cache;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisSentinelConfiguration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

/**
 * Redis client configuration for the yoCat Office backend cache layer (EDD §3.3 / §3.5 / §11.3 /
 * §4.5.9 Cache Contract; ARCH §7 Deployment Topology).
 *
 * <p>Owns the single {@link RedisConnectionFactory} and the {@link StringRedisTemplate} that every
 * BC cache component ({@code member.infrastructure.SessionCache}, {@code
 * leaderboard.infrastructure.LeaderboardCache}) and the cross-cutting {@link RateLimiter} share.
 * The template is defined here explicitly (rather than relying on Spring Boot's
 * {@code RedisAutoConfiguration}) so the cache beans resolve in every runtime profile — including
 * the persistence integration-test slice, which excludes {@code RedisAutoConfiguration}.</p>
 *
 * <h2>Topology (ARCH §7 / EDD §3.5)</h2>
 * <ul>
 *   <li><b>Standalone</b> (local / single node, EDD §3.7.2) — default when no Sentinel master is
 *       configured; host/port come from {@code spring.data.redis.*} (no hardcoded host).</li>
 *   <li><b>Sentinel HA</b> (staging / prod, EDD §3.6 / ARCH §7.3) — enabled by setting
 *       {@code yocat.cache.redis.sentinel.master} + {@code yocat.cache.redis.sentinel.nodes}
 *       (3 nodes, quorum selection); session/cache fail over without data-plane changes.</li>
 * </ul>
 *
 * <h2>Serialization (EDD §11.3B)</h2>
 * All cached values are {@code String}/{@code String(JSON)} per EDD §11.3B ("SET EX / GET, O(1)
 * K-V" and "整頁快取"); the shared {@link StringRedisTemplate} uses UTF-8 string serializers for
 * keys and values, so no mixed/binary serializer is introduced. Values are small (session ~300 B,
 * leaderboard page ~8 KB per EDD §11.3D), and EDD §11.3B pins the on-wire form to plain
 * {@code String(JSON)} with no compression layer, so none is added (Iron Law §3: doc is
 * authoritative over the {@code >1KB} compression default).
 *
 * <h2>Pooling / timeout (EDD §11.2 / §4.5.9)</h2>
 * Lettuce commons-pool2 pool capped at {@code yocat.cache.redis.pool.max-active} (default 16, EDD
 * §11.2 Bulkhead "Redis pool=16"); command timeout {@code yocat.cache.redis.timeout-ms} (default
 * 500 ms, EDD §4.5.9 "API → Redis 500ms"). The factory connects lazily, so application/test
 * context start-up never blocks on Redis availability (resilience: cache is a degradable
 * dependency, EDD §8.3).
 *
 * <h2>AUTH (EDD §7.5 / §13.6)</h2>
 * Redis password is read from {@code yocat.cache.redis.password} (sourced from the
 * {@code REDIS_PASSWORD} secret); never a literal in source or committed config.
 */
@Configuration
public class RedisConfig {

    private final String host;
    private final int port;
    private final String password;
    private final String sentinelMaster;
    private final String sentinelNodes;
    private final int poolMaxActive;
    private final int poolMaxIdle;
    private final int poolMinIdle;
    private final long timeoutMs;

    public RedisConfig(
            @Value("${spring.data.redis.host:localhost}") String host,
            @Value("${spring.data.redis.port:6379}") int port,
            @Value("${yocat.cache.redis.password:${REDIS_PASSWORD:}}") String password,
            @Value("${yocat.cache.redis.sentinel.master:}") String sentinelMaster,
            @Value("${yocat.cache.redis.sentinel.nodes:}") String sentinelNodes,
            @Value("${yocat.cache.redis.pool.max-active:16}") int poolMaxActive,
            @Value("${yocat.cache.redis.pool.max-idle:16}") int poolMaxIdle,
            @Value("${yocat.cache.redis.pool.min-idle:4}") int poolMinIdle,
            @Value("${yocat.cache.redis.timeout-ms:500}") long timeoutMs) {
        this.host = host;
        this.port = port;
        this.password = password;
        this.sentinelMaster = sentinelMaster;
        this.sentinelNodes = sentinelNodes;
        this.poolMaxActive = poolMaxActive;
        this.poolMaxIdle = poolMaxIdle;
        this.poolMinIdle = poolMinIdle;
        this.timeoutMs = timeoutMs;
    }

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisConfiguration topology = buildTopology();

        GenericObjectPoolConfig<Object> poolConfig = new GenericObjectPoolConfig<>();
        poolConfig.setMaxTotal(poolMaxActive);
        poolConfig.setMaxIdle(poolMaxIdle);
        poolConfig.setMinIdle(poolMinIdle);
        poolConfig.setMaxWait(Duration.ofMillis(timeoutMs));

        LettuceClientConfiguration clientConfig = LettucePoolingClientConfiguration.builder()
                .commandTimeout(Duration.ofMillis(timeoutMs))
                .poolConfig(poolConfig)
                .build();

        LettuceConnectionFactory factory = new LettuceConnectionFactory(topology, clientConfig);
        // Lazy: the native connection is opened on first use, so context boot never blocks on Redis.
        factory.setEagerInitialization(false);
        factory.setValidateConnection(false);
        return factory;
    }

    private RedisConfiguration buildTopology() {
        if (StringUtils.hasText(sentinelMaster) && StringUtils.hasText(sentinelNodes)) {
            List<String> nodes = Arrays.stream(sentinelNodes.split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .toList();
            RedisSentinelConfiguration sentinel = new RedisSentinelConfiguration(sentinelMaster, Set.copyOf(nodes));
            if (StringUtils.hasText(password)) {
                sentinel.setPassword(RedisPassword.of(password));
            }
            return sentinel;
        }
        RedisStandaloneConfiguration standalone = new RedisStandaloneConfiguration(host, port);
        if (StringUtils.hasText(password)) {
            standalone.setPassword(RedisPassword.of(password));
        }
        return standalone;
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        StringRedisTemplate template = new StringRedisTemplate(connectionFactory);
        // EDD §11.3B: keys and values are UTF-8 String / String(JSON); no binary/JDK serializer.
        template.setEnableTransactionSupport(false);
        template.afterPropertiesSet();
        return template;
    }
}
