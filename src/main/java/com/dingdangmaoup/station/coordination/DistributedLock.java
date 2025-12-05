package com.dingdangmaoup.station.coordination;

import com.dingdangmaoup.station.config.properties.CoordinationProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Redis-based distributed lock implementation
 */
@Slf4j
@Component
public class DistributedLock {

    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;
    private final Duration lockTtl;
    private final Duration waitTimeout;

    @Value("${station.node.id}")
    private String nodeId;

    public DistributedLock(ReactiveRedisTemplate<String, String> reactiveRedisTemplate,
                           CoordinationProperties coordinationProperties) {
        this.reactiveRedisTemplate = reactiveRedisTemplate;
        this.lockTtl = coordinationProperties.getLock().getTtl();
        this.waitTimeout = coordinationProperties.getLock().getWaitTimeout();
    }

    /**
     * Acquire a distributed lock
     */
    public Mono<Boolean> acquireLock(String lockKey) {
        String redisKey = "lock:" + lockKey;
        return reactiveRedisTemplate.opsForValue()
                .setIfAbsent(redisKey, nodeId, lockTtl)
                .doOnNext(acquired -> {
                    if (acquired) {
                        log.debug("Acquired lock: {} by node: {}", lockKey, nodeId);
                    } else {
                        log.debug("Failed to acquire lock: {} (held by another node)", lockKey);
                    }
                });
    }

    /**
     * Release a distributed lock
     */
    public Mono<Boolean> releaseLock(String lockKey) {
        String redisKey = "lock:" + lockKey;
        return reactiveRedisTemplate.opsForValue()
                .get(redisKey)
                .flatMap(owner -> {
                    if (nodeId.equals(owner)) {
                        return reactiveRedisTemplate.delete(redisKey)
                                .map(count -> count > 0)
                                .doOnNext(released -> {
                                    if (released) {
                                        log.debug("Released lock: {} by node: {}", lockKey, nodeId);
                                    }
                                });
                    } else {
                        log.warn("Cannot release lock: {} (owned by: {}, current: {})",
                                lockKey, owner, nodeId);
                        return Mono.just(false);
                    }
                })
                .defaultIfEmpty(false);
    }

    /**
     * Try to acquire lock with retry
     */
    public Mono<Boolean> tryAcquireLock(String lockKey, Duration timeout) {
        return acquireLock(lockKey)
                .flatMap(acquired -> {
                    if (acquired) {
                        return Mono.just(true);
                    }

                    return Mono.delay(Duration.ofMillis(100))
                            .flatMap(tick -> tryAcquireLock(lockKey, timeout.minus(Duration.ofMillis(100))))
                            .timeout(timeout)
                            .onErrorReturn(false);
                });
    }

    /**
     * Execute action with lock
     */
    public <T> Mono<T> withLock(String lockKey, Mono<T> action) {
        return acquireLock(lockKey)
                .flatMap(acquired -> {
                    if (!acquired) {
                        return Mono.error(new LockException("Failed to acquire lock: " + lockKey));
                    }

                    return action
                            .doFinally(signalType -> releaseLock(lockKey).subscribe());
                });
    }

    public static class LockException extends RuntimeException {
        public LockException(String message) {
            super(message);
        }
    }
}
