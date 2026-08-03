package com.talentledger.infrastructure.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.talentledger.application.port.outbound.CachePort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * ADR-004: Caffeine (L1, in-process) is the default — zero extra infra,
 * fine for a single instance. Set {@code CACHE_PROVIDER=REDIS} once
 * horizontally scaled to 2+ backend instances, where an in-process-only
 * cache means each instance sees a different, potentially stale copy of
 * the same key. Same {@link CachePort} interface either way — nothing
 * else in the codebase needs to know or care which one is active.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCacheSpecification("maximumSize=10000,expireAfterWrite=10m");
        manager.setCacheNames(java.util.List.of(
                "user-profile", "user-quota", "dump-list",
                "search", "system-config", "session"
        ));
        return manager;
    }

    @Bean
    public Cache<String, CacheEntry> caffeineCache() {
        return Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "talentledger.cache.provider", havingValue = "CAFFEINE", matchIfMissing = true)
    public CachePort caffeineCachePort(Cache<String, CacheEntry> caffeineCache) {
        return new CaffeineCacheAdapter(caffeineCache);
    }

    @Bean
    @ConditionalOnProperty(name = "talentledger.cache.provider", havingValue = "REDIS")
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    @ConditionalOnProperty(name = "talentledger.cache.provider", havingValue = "REDIS")
    public CachePort redisCachePort(RedisTemplate<String, Object> redisTemplate) {
        return new RedisCacheAdapter(redisTemplate);
    }

    /** A cached value paired with its own expiry, since Caffeine's own TTL is a single global setting. */
    record CacheEntry(Object value, long expiresAtEpochMs) {
        boolean isExpired() {
            return System.currentTimeMillis() > expiresAtEpochMs;
        }
    }

    /**
     * L1 cache adapter. Previously this ignored the caller-supplied TTL
     * entirely (put() always used Caffeine's single global 10-minute
     * expiry) — now each entry tracks and honors its own requested TTL.
     */
    static class CaffeineCacheAdapter implements CachePort {

        private final Cache<String, CacheEntry> cache;

        CaffeineCacheAdapter(Cache<String, CacheEntry> cache) {
            this.cache = cache;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> Optional<T> get(String key, Class<T> type) {
            CacheEntry entry = cache.getIfPresent(key);
            if (entry == null) return Optional.empty();
            if (entry.isExpired()) {
                cache.invalidate(key);
                return Optional.empty();
            }
            return Optional.ofNullable(type.cast(entry.value()));
        }

        @Override
        public <T> void put(String key, T value, long ttl, TimeUnit unit) {
            long expiresAt = System.currentTimeMillis() + unit.toMillis(ttl);
            cache.put(key, new CacheEntry(value, expiresAt));
        }

        @Override
        public void invalidate(String key) {
            cache.invalidate(key);
        }

        @Override
        public void invalidateByPrefix(String prefix) {
            cache.asMap().keySet().removeIf(key -> key.startsWith(prefix));
        }

        @Override
        public boolean containsKey(String key) {
            CacheEntry entry = cache.getIfPresent(key);
            return entry != null && !entry.isExpired();
        }
    }

    /**
     * L2 cache adapter backed by Redis. Shared across all backend instances,
     * unlike Caffeine — the point of promoting to this once horizontally
     * scaled. TTL is a real Redis expiry (EXPIRE), not emulated in-app.
     */
    static class RedisCacheAdapter implements CachePort {

        private final RedisTemplate<String, Object> redisTemplate;
        private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RedisCacheAdapter.class);

        RedisCacheAdapter(RedisTemplate<String, Object> redisTemplate) {
            this.redisTemplate = redisTemplate;
        }

        @Override
        public <T> Optional<T> get(String key, Class<T> type) {
            try {
                Object value = redisTemplate.opsForValue().get(key);
                return Optional.ofNullable(value).map(type::cast);
            } catch (Exception e) {
                // Fail open: a cache that's down should degrade to "miss", not break the caller.
                log.warn("Redis GET failed for key '{}', treating as cache miss: {}", key, e.getMessage());
                return Optional.empty();
            }
        }

        @Override
        public <T> void put(String key, T value, long ttl, TimeUnit unit) {
            try {
                redisTemplate.opsForValue().set(key, value, Duration.ofMillis(unit.toMillis(ttl)));
            } catch (Exception e) {
                log.warn("Redis PUT failed for key '{}', continuing without caching: {}", key, e.getMessage());
            }
        }

        @Override
        public void invalidate(String key) {
            try {
                redisTemplate.delete(key);
            } catch (Exception e) {
                log.warn("Redis DELETE failed for key '{}': {}", key, e.getMessage());
            }
        }

        @Override
        public void invalidateByPrefix(String prefix) {
            try {
                // KEYS blocks the entire Redis server while it scans the full keyspace —
                // a real problem once the dataset is more than trivially small. SCAN walks
                // the keyspace incrementally via a cursor with no single blocking call.
                Set<String> keys = new java.util.HashSet<>();
                org.springframework.data.redis.core.ScanOptions options =
                        org.springframework.data.redis.core.ScanOptions.scanOptions()
                                .match(prefix + "*")
                                .count(500)
                                .build();
                try (org.springframework.data.redis.core.Cursor<byte[]> cursor =
                             redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<org.springframework.data.redis.core.Cursor<byte[]>>) connection ->
                                     connection.scan(options))) {
                    java.nio.charset.Charset charset = java.nio.charset.StandardCharsets.UTF_8;
                    while (cursor.hasNext()) {
                        keys.add(new String(cursor.next(), charset));
                    }
                }
                if (!keys.isEmpty()) {
                    redisTemplate.delete(keys);
                }
            } catch (Exception e) {
                log.warn("Redis prefix invalidation failed for prefix '{}': {}", prefix, e.getMessage());
            }
        }

        @Override
        public boolean containsKey(String key) {
            try {
                return Boolean.TRUE.equals(redisTemplate.hasKey(key));
            } catch (Exception e) {
                log.warn("Redis EXISTS check failed for key '{}': {}", key, e.getMessage());
                return false;
            }
        }
    }
}
