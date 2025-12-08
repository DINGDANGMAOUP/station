package com.dingdangmaoup.station.cache;

import com.dingdangmaoup.station.metrics.CacheMetrics;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Optional;

/**
 * Multi-level cache manager that orchestrates cache lookups across multiple layers:
 * L1: Local Caffeine cache (fastest, ~0ms)
 * L2: Redis distributed cache (~1ms)
 * L3: Peer nodes via gRPC (~10ms)
 * L4: Docker Hub (slowest, ~100-500ms)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MultiLevelCacheManager {

    private final LocalCacheManager localCache;
    private final RedisCacheManager redisCache;
    private final CacheMetrics cacheMetrics;

    @Value("${station.node.id}")
    private String nodeId;

    /**
     * Get cache entry with multi-level lookup
     */
    public Mono<Optional<CacheEntry>> get(CacheKey key) {
        // L1: Local cache
        Timer.Sample localTimer = cacheMetrics.startLocalCacheTimer();
        return localCache.get(key)
                .flatMap(localResult -> {
                    cacheMetrics.recordLocalCacheLatency(localTimer);

                    if (localResult.isPresent()) {
                        log.debug("Cache HIT at L1 (Local): {}", key);
                        cacheMetrics.recordLocalCacheHit();
                        return Mono.just(localResult);
                    }

                    cacheMetrics.recordLocalCacheMiss();

                    // L2: Redis cache
                    Timer.Sample redisTimer = cacheMetrics.startRedisCacheTimer();
                    return redisCache.get(key)
                            .flatMap(redisResult -> {
                                cacheMetrics.recordRedisCacheLatency(redisTimer);

                                if (redisResult.isPresent()) {
                                    log.debug("Cache HIT at L2 (Redis): {}", key);
                                    cacheMetrics.recordRedisCacheHit();
                                    // Populate L1 cache
                                    return localCache.put(key, redisResult.get())
                                            .thenReturn(redisResult);
                                }

                                // L2 miss - caller should check L3 (peers) or L4 (Docker Hub)
                                log.debug("Cache MISS at all levels: {}", key);
                                cacheMetrics.recordRedisCacheMiss();
                                return Mono.just(Optional.<CacheEntry>empty());
                            });
                });
    }

    /**
     * Put entry into all cache levels
     */
    public Mono<Void> put(CacheKey key, CacheEntry entry) {
        // Ensure nodeId is set
        if (entry.getNodeId() == null) {
            entry.setNodeId(nodeId);
        }

        return Mono.when(
                localCache.put(key, entry),
                redisCache.put(key, entry)
        ).doOnSuccess(v -> log.debug("Cache PUT to all levels: {}", key));
    }

    /**
     * Evict entry from all cache levels
     */
    public Mono<Void> evict(CacheKey key) {
        return Mono.when(
                localCache.evict(key),
                redisCache.evict(key)
        ).doOnSuccess(v -> log.debug("Cache EVICT from all levels: {}", key));
    }

    /**
     * Clear all caches
     */
    public Mono<Void> clearAll() {
        return Mono.when(
                localCache.clear(),
                redisCache.clear()
        ).doOnSuccess(v -> log.info("All caches CLEARED"));
    }

    /**
     * Get cache statistics
     */
    public Mono<CacheStats> getStats() {
        return Mono.zip(
                localCache.size(),
                localCache.hitRate()
        ).map(tuple -> CacheStats.builder()
                .localSize(tuple.getT1())
                .localHitRate(tuple.getT2())
                .build());
    }

    @lombok.Data
    @lombok.Builder
    public static class CacheStats {
        private Long localSize;
        private Double localHitRate;
    }
}
