package com.aem.oak.publish.content;

import org.osgi.service.component.annotations.*;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Redis-backed cache service for content delivery on Publish tier.
 * Provides shared caching across all publish instances with TTL-based expiration.
 */
@Component(service = RedisCacheService.class, immediate = true)
@Designate(ocd = RedisCacheService.Config.class)
public class RedisCacheService {

    private static final Logger LOG = LoggerFactory.getLogger(RedisCacheService.class);
    private static final String CACHE_PREFIX = "aem:cache:";
    private static final String CONTENT_TYPE_SUFFIX = ":ct";

    @ObjectClassDefinition(name = "AEM Oak Redis Cache Service Configuration")
    public @interface Config {
        @AttributeDefinition(name = "Enabled", description = "Enable Redis caching")
        boolean enabled() default true;

        @AttributeDefinition(name = "Redis Host", description = "Redis server hostname")
        String redisHost() default "redis";

        @AttributeDefinition(name = "Redis Port", description = "Redis server port")
        int redisPort() default 6379;

        @AttributeDefinition(name = "TTL (seconds)", description = "Time-to-live for cache entries")
        int ttlSeconds() default 300;

        @AttributeDefinition(name = "Max Entry Size (bytes)", description = "Maximum size per cache entry")
        int maxEntrySize() default 1048576;

        @AttributeDefinition(name = "Connection Pool Size", description = "Maximum connections in pool")
        int poolSize() default 10;

        @AttributeDefinition(name = "Connection Timeout (ms)", description = "Connection timeout in milliseconds")
        int connectionTimeout() default 2000;
    }

    private final AtomicLong hits = new AtomicLong(0);
    private final AtomicLong misses = new AtomicLong(0);
    private final AtomicLong evictions = new AtomicLong(0);

    private Config config;
    private JedisPool jedisPool;

    @Activate
    protected void activate(Config config) {
        this.config = config;

        if (config.enabled()) {
            try {
                JedisPoolConfig poolConfig = new JedisPoolConfig();
                poolConfig.setMaxTotal(config.poolSize());
                poolConfig.setMaxIdle(config.poolSize() / 2);
                poolConfig.setMinIdle(1);
                poolConfig.setTestOnBorrow(true);
                poolConfig.setTestOnReturn(true);
                poolConfig.setMaxWait(Duration.ofMillis(config.connectionTimeout()));

                jedisPool = new JedisPool(poolConfig, config.redisHost(), config.redisPort(),
                        config.connectionTimeout());

                // Test connection
                try (Jedis jedis = jedisPool.getResource()) {
                    String pong = jedis.ping();
                    LOG.info("Redis cache service activated, host={}:{}, ttl={}s, ping={}",
                            config.redisHost(), config.redisPort(), config.ttlSeconds(), pong);
                }
            } catch (Exception e) {
                LOG.error("Failed to connect to Redis at {}:{}", config.redisHost(), config.redisPort(), e);
                jedisPool = null;
            }
        } else {
            LOG.info("Redis cache service is disabled");
        }
    }

    @Deactivate
    protected void deactivate() {
        if (jedisPool != null) {
            jedisPool.close();
            jedisPool = null;
        }
        LOG.info("Redis cache service deactivated");
    }

    @Modified
    protected void modified(Config config) {
        deactivate();
        activate(config);
    }

    /**
     * Check if Redis is available.
     */
    public boolean isAvailable() {
        return config.enabled() && jedisPool != null && !jedisPool.isClosed();
    }

    /**
     * Get cached content.
     */
    public CacheService.CacheEntry get(String path, String selector, String extension) {
        if (!isAvailable()) {
            return null;
        }

        String key = buildKey(path, selector, extension);

        try (Jedis jedis = jedisPool.getResource()) {
            String data = jedis.get(key);
            if (data == null) {
                misses.incrementAndGet();
                return null;
            }

            String contentType = jedis.get(key + CONTENT_TYPE_SUFFIX);
            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            hits.incrementAndGet();
            byte[] decodedData = Base64.getDecoder().decode(data);
            return new CacheService.CacheEntry(decodedData, contentType);

        } catch (Exception e) {
            LOG.warn("Failed to get from Redis cache: {}", key, e);
            misses.incrementAndGet();
            return null;
        }
    }

    /**
     * Put content in cache.
     */
    public void put(String path, String selector, String extension, CacheService.CacheEntry entry) {
        if (!isAvailable()) {
            return;
        }

        if (entry.getData().length > config.maxEntrySize()) {
            LOG.debug("Entry too large for cache: {} bytes", entry.getData().length);
            return;
        }

        String key = buildKey(path, selector, extension);

        try (Jedis jedis = jedisPool.getResource()) {
            String encodedData = Base64.getEncoder().encodeToString(entry.getData());
            jedis.setex(key, config.ttlSeconds(), encodedData);
            jedis.setex(key + CONTENT_TYPE_SUFFIX, config.ttlSeconds(), entry.getContentType());

            LOG.debug("Cached in Redis: {} (size={} bytes)", key, entry.getData().length);

        } catch (Exception e) {
            LOG.warn("Failed to put in Redis cache: {}", key, e);
        }
    }

    /**
     * Invalidate cache entry for a specific path.
     */
    public void invalidate(String path) {
        if (!isAvailable()) {
            return;
        }

        String pattern = CACHE_PREFIX + path + ":*";

        try (Jedis jedis = jedisPool.getResource()) {
            Set<String> keys = jedis.keys(pattern);
            if (!keys.isEmpty()) {
                jedis.del(keys.toArray(new String[0]));
                evictions.addAndGet(keys.size());
                LOG.debug("Invalidated {} Redis cache entries for: {}", keys.size(), path);
            }
        } catch (Exception e) {
            LOG.warn("Failed to invalidate Redis cache for: {}", path, e);
        }
    }

    /**
     * Invalidate cache entries matching a path pattern.
     */
    public void invalidatePattern(String pathPattern) {
        if (!isAvailable()) {
            return;
        }

        String redisPattern = CACHE_PREFIX + pathPattern.replace("*", "*") + ":*";

        try (Jedis jedis = jedisPool.getResource()) {
            Set<String> keys = jedis.keys(redisPattern);
            if (!keys.isEmpty()) {
                jedis.del(keys.toArray(new String[0]));
                evictions.addAndGet(keys.size());
                LOG.debug("Invalidated {} Redis cache entries for pattern: {}", keys.size(), pathPattern);
            }
        } catch (Exception e) {
            LOG.warn("Failed to invalidate Redis cache for pattern: {}", pathPattern, e);
        }
    }

    /**
     * Clear entire cache.
     */
    public void clear() {
        if (!isAvailable()) {
            return;
        }

        String pattern = CACHE_PREFIX + "*";

        try (Jedis jedis = jedisPool.getResource()) {
            Set<String> keys = jedis.keys(pattern);
            if (!keys.isEmpty()) {
                jedis.del(keys.toArray(new String[0]));
                evictions.addAndGet(keys.size());
                LOG.info("Cleared {} Redis cache entries", keys.size());
            }
        } catch (Exception e) {
            LOG.warn("Failed to clear Redis cache", e);
        }
    }

    /**
     * Get cache statistics.
     */
    public CacheStats getStats() {
        long totalHits = hits.get();
        long totalMisses = misses.get();
        long totalRequests = totalHits + totalMisses;
        double hitRate = totalRequests > 0 ? (double) totalHits / totalRequests : 0.0;

        int entries = 0;
        long totalSize = 0;

        if (isAvailable()) {
            try (Jedis jedis = jedisPool.getResource()) {
                Set<String> keys = jedis.keys(CACHE_PREFIX + "*");
                // Count only data keys, not content-type keys
                entries = (int) keys.stream()
                        .filter(k -> !k.endsWith(CONTENT_TYPE_SUFFIX))
                        .count();
            } catch (Exception e) {
                LOG.warn("Failed to get Redis cache stats", e);
            }
        }

        return new CacheStats(
                entries,
                -1, // No max in Redis (controlled by maxmemory)
                totalSize,
                totalHits,
                totalMisses,
                evictions.get(),
                hitRate
        );
    }

    private String buildKey(String path, String selector, String extension) {
        return CACHE_PREFIX + path + ":" +
                (selector != null ? selector : "") + ":" +
                (extension != null ? extension : "");
    }

    /**
     * Cache statistics.
     */
    public static class CacheStats {
        private final int entries;
        private final int maxEntries;
        private final long totalSize;
        private final long hits;
        private final long misses;
        private final long evictions;
        private final double hitRate;

        public CacheStats(int entries, int maxEntries, long totalSize,
                         long hits, long misses, long evictions, double hitRate) {
            this.entries = entries;
            this.maxEntries = maxEntries;
            this.totalSize = totalSize;
            this.hits = hits;
            this.misses = misses;
            this.evictions = evictions;
            this.hitRate = hitRate;
        }

        public int getEntries() { return entries; }
        public int getMaxEntries() { return maxEntries; }
        public long getTotalSize() { return totalSize; }
        public long getHits() { return hits; }
        public long getMisses() { return misses; }
        public long getEvictions() { return evictions; }
        public double getHitRate() { return hitRate; }
    }
}
