package com.dingdangmaoup.station.cache;

import com.github.benmanes.caffeine.cache.Cache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class LocalCacheManager {

    private final Cache<String, Object> localCache;

    public Mono<Optional<CacheEntry>> get(CacheKey key) {
        return Mono.fromCallable(() -> {
            Object value = localCache.getIfPresent(key.toRedisKey());
            if (value instanceof CacheEntry entry) {
                log.debug("Local cache HIT: {}", key);
                return Optional.of(entry);
            }
            log.debug("Local cache MISS: {}", key);
            return Optional.empty();
        });
    }

    public Mono<Void> put(CacheKey key, CacheEntry entry) {
        return Mono.fromRunnable(() -> {
            localCache.put(key.toRedisKey(), entry);
            log.debug("Local cache PUT: {}", key);
        });
    }

    public Mono<Void> evict(CacheKey key) {
        return Mono.fromRunnable(() -> {
            localCache.invalidate(key.toRedisKey());
            log.debug("Local cache EVICT: {}", key);
        });
    }

    public Mono<Void> clear() {
        return Mono.fromRunnable(() -> {
            localCache.invalidateAll();
            log.info("Local cache CLEARED");
        });
    }

    public Mono<Long> size() {
        return Mono.fromCallable(localCache::estimatedSize);
    }

    public Mono<Double> hitRate() {
        return Mono.fromCallable(() -> localCache.stats().hitRate());
    }
}
