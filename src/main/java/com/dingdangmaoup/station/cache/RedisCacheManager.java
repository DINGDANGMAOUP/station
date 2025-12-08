package com.dingdangmaoup.station.cache;

import com.dingdangmaoup.station.config.properties.CacheProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisCacheManager {

    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;
    private final CacheProperties cacheProperties;
    private final ObjectMapper objectMapper ;

    public Mono<Optional<CacheEntry>> get(CacheKey key) {
        return reactiveRedisTemplate.opsForValue()
                .get(key.toRedisKey())
                .map(json -> {
                    try {
                        CacheEntry entry = objectMapper.readValue(json, CacheEntry.class);
                        log.debug("Redis cache HIT: {}", key);
                        return Optional.of(entry);
                    } catch (JsonProcessingException e) {
                        log.error("Failed to deserialize cache entry", e);
                        return Optional.<CacheEntry>empty();
                    }
                })
                .defaultIfEmpty(Optional.empty())
                .doOnNext(opt -> {
                    if (opt.isEmpty()) {
                        log.debug("Redis cache MISS: {}", key);
                    }
                });
    }

    public Mono<Void> put(CacheKey key, CacheEntry entry) {
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(entry))
                .flatMap(json -> {
                    Duration ttl = "manifest".equals(key.getType())
                            ? cacheProperties.getRedis().getManifestTtl()
                            : cacheProperties.getRedis().getBlobTtl();
                    return reactiveRedisTemplate.opsForValue()
                            .set(key.toRedisKey(), json, ttl);
                })
                .doOnSuccess(success -> log.debug("Redis cache PUT: {}", key))
                .then();
    }

    public Mono<Void> evict(CacheKey key) {
        return reactiveRedisTemplate.delete(key.toRedisKey())
                .doOnSuccess(count -> log.debug("Redis cache EVICT: {} (deleted: {})", key, count))
                .then();
    }

    public Mono<Void> clear() {
        return reactiveRedisTemplate.execute(connection ->
                        connection.serverCommands().flushDb())
                .doOnNext(success -> log.info("Redis cache CLEARED"))
                .then();
    }
}
