package com.aem.oak.publish.content;

import org.osgi.service.component.annotations.*;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory cache service for content delivery on Publish tier.
 * Provides fast response caching with TTL-based expiration.
 */
@Component(service = CacheService.class, immediate = true)
@Designate(ocd = CacheService.Config.class)
public class CacheService {

    private static final Logger LOG = LoggerFactory.getLogger(CacheService.class);

    @ObjectClassDefinition(name = "AEM Oak Cache Service Configuration")
    public @interface Config {
        @AttributeDefinition(name = "Enabled", description = "Enable caching")
        boolean enabled() default true;

        @AttributeDefinition(name = "Max Entries", description = "Maximum number of cache entries")
        int maxEntries() default 10000;

        @AttributeDefinition(name = "TTL (seconds)", description = "Time-to-live for cache entries")
        int ttlSeconds() default 300;

        @AttributeDefinition(name = "Max Entry Size (bytes)", description = "Maximum size per cache entry")
        int maxEntrySize() default 1048576;

        @AttributeDefinition(name = "Cleanup Interval (seconds)", description = "Interval for cleaning expired entries")
        int cleanupIntervalSeconds() default 60;
    }

    private final Map<String, CacheItem> cache = new ConcurrentHashMap<>();
    private final AtomicLong hits = new AtomicLong(0);
    private final AtomicLong misses = new AtomicLong(0);
    private final AtomicLong evictions = new AtomicLong(0);

    private Config config;
    private ScheduledExecutorService cleanupScheduler;

    @Activate
    protected void activate(Config config) {
        this.config = config;

        if (config.enabled()) {
            // Start cleanup scheduler
            cleanupScheduler = Executors.newSingleThreadScheduledExecutor();
            cleanupScheduler.scheduleAtFixedRate(
                    this::cleanup,
                    config.cleanupIntervalSeconds(),
                    config.cleanupIntervalSeconds(),
                    TimeUnit.SECONDS);

            LOG.info("Cache service activated, maxEntries={}, ttl={}s",
                    config.maxEntries(), config.ttlSeconds());
        } else {
            LOG.info("Cache service is disabled");
        }
    }

    @Deactivate
    protected void deactivate() {
        if (cleanupScheduler != null) {
            cleanupScheduler.shutdown();
            try {
                if (!cleanupScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    cleanupScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                cleanupScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        cache.clear();
        LOG.info("Cache service deactivated");
    }

    @Modified
    protected void modified(Config config) {
        deactivate();
        activate(config);
    }

    /**
     * Get cached content.
     */
    public CacheEntry get(String path, String selector, String extension) {
        if (!config.enabled()) {
            return null;
        }

        String key = buildKey(path, selector, extension);
        CacheItem item = cache.get(key);

        if (item == null) {
            misses.incrementAndGet();
            return null;
        }

        if (item.isExpired()) {
            cache.remove(key);
            misses.incrementAndGet();
            return null;
        }

        hits.incrementAndGet();
        item.recordAccess();
        return item.getEntry();
    }

    /**
     * Put content in cache.
     */
    public void put(String path, String selector, String extension, CacheEntry entry) {
        if (!config.enabled()) {
            return;
        }

        if (entry.getData().length > config.maxEntrySize()) {
            LOG.debug("Entry too large for cache: {} bytes", entry.getData().length);
            return;
        }

        // Check cache size and evict if necessary
        if (cache.size() >= config.maxEntries()) {
            evictOldest();
        }

        String key = buildKey(path, selector, extension);
        long expirationTime = System.currentTimeMillis() + (config.ttlSeconds() * 1000L);

        cache.put(key, new CacheItem(entry, expirationTime));
        LOG.debug("Cached: {} (size={} bytes)", key, entry.getData().length);
    }

    /**
     * Invalidate cache entry for a specific path.
     */
    public void invalidate(String path) {
        if (!config.enabled()) {
            return;
        }

        String prefix = path + ":";
        cache.entrySet().removeIf(entry -> {
            if (entry.getKey().startsWith(prefix) || entry.getKey().equals(path + "::")) {
                evictions.incrementAndGet();
                return true;
            }
            return false;
        });

        LOG.debug("Invalidated cache for: {}", path);
    }

    /**
     * Invalidate cache entries matching a path pattern.
     */
    public void invalidatePattern(String pathPattern) {
        if (!config.enabled()) {
            return;
        }

        String regex = pathPattern.replace("*", ".*");
        cache.entrySet().removeIf(entry -> {
            String keyPath = entry.getKey().split(":")[0];
            if (keyPath.matches(regex)) {
                evictions.incrementAndGet();
                return true;
            }
            return false;
        });

        LOG.debug("Invalidated cache for pattern: {}", pathPattern);
    }

    /**
     * Clear entire cache.
     */
    public void clear() {
        int size = cache.size();
        cache.clear();
        evictions.addAndGet(size);
        LOG.info("Cache cleared, {} entries removed", size);
    }

    /**
     * Get cache statistics.
     */
    public CacheStats getStats() {
        long totalHits = hits.get();
        long totalMisses = misses.get();
        long totalRequests = totalHits + totalMisses;
        double hitRate = totalRequests > 0 ? (double) totalHits / totalRequests : 0.0;

        long totalSize = cache.values().stream()
                .mapToLong(item -> item.getEntry().getData().length)
                .sum();

        return new CacheStats(
                cache.size(),
                config.maxEntries(),
                totalSize,
                totalHits,
                totalMisses,
                evictions.get(),
                hitRate
        );
    }

    private String buildKey(String path, String selector, String extension) {
        return path + ":" + (selector != null ? selector : "") + ":" + (extension != null ? extension : "");
    }

    private void evictOldest() {
        // Simple LRU eviction - remove the entry with oldest access time
        cache.entrySet().stream()
                .min((a, b) -> Long.compare(a.getValue().getLastAccessTime(), b.getValue().getLastAccessTime()))
                .ifPresent(oldest -> {
                    cache.remove(oldest.getKey());
                    evictions.incrementAndGet();
                });
    }

    private void cleanup() {
        long now = System.currentTimeMillis();
        int removed = 0;

        var iterator = cache.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getValue().getExpirationTime() <= now) {
                iterator.remove();
                removed++;
            }
        }

        if (removed > 0) {
            evictions.addAndGet(removed);
            LOG.debug("Cleanup removed {} expired entries", removed);
        }
    }

    /**
     * Cache entry containing the cached data.
     */
    public static class CacheEntry {
        private final byte[] data;
        private final String contentType;

        public CacheEntry(byte[] data, String contentType) {
            this.data = data;
            this.contentType = contentType;
        }

        public byte[] getData() {
            return data;
        }

        public String getContentType() {
            return contentType;
        }
    }

    /**
     * Internal cache item with metadata.
     */
    private static class CacheItem {
        private final CacheEntry entry;
        private final long expirationTime;
        private volatile long lastAccessTime;

        public CacheItem(CacheEntry entry, long expirationTime) {
            this.entry = entry;
            this.expirationTime = expirationTime;
            this.lastAccessTime = System.currentTimeMillis();
        }

        public CacheEntry getEntry() {
            return entry;
        }

        public long getExpirationTime() {
            return expirationTime;
        }

        public long getLastAccessTime() {
            return lastAccessTime;
        }

        public void recordAccess() {
            this.lastAccessTime = System.currentTimeMillis();
        }

        public boolean isExpired() {
            return System.currentTimeMillis() >= expirationTime;
        }
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
