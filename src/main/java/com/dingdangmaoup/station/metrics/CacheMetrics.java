package com.dingdangmaoup.station.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Cache metrics collection
 */
@Slf4j
@Component
public class CacheMetrics {

    private final Counter localCacheHits;
    private final Counter localCacheMisses;
    private final Counter redisCacheHits;
    private final Counter redisCacheMisses;
    private final Counter peerCacheHits;
    private final Counter peerCacheMisses;
    private final Counter dockerHubHits;

    private final Timer localCacheLatency;
    private final Timer redisCacheLatency;
    private final Timer peerCacheLatency;
    private final Timer dockerHubLatency;

    public CacheMetrics(MeterRegistry meterRegistry) {
        this.localCacheHits = Counter.builder("station.cache.hit")
                .tag("level", "local")
                .description("Number of local cache hits")
                .register(meterRegistry);

        this.localCacheMisses = Counter.builder("station.cache.miss")
                .tag("level", "local")
                .description("Number of local cache misses")
                .register(meterRegistry);

        this.redisCacheHits = Counter.builder("station.cache.hit")
                .tag("level", "redis")
                .description("Number of Redis cache hits")
                .register(meterRegistry);

        this.redisCacheMisses = Counter.builder("station.cache.miss")
                .tag("level", "redis")
                .description("Number of Redis cache misses")
                .register(meterRegistry);

        this.peerCacheHits = Counter.builder("station.cache.hit")
                .tag("level", "peer")
                .description("Number of peer cache hits")
                .register(meterRegistry);

        this.peerCacheMisses = Counter.builder("station.cache.miss")
                .tag("level", "peer")
                .description("Number of peer cache misses")
                .register(meterRegistry);

        this.dockerHubHits = Counter.builder("station.cache.hit")
                .tag("level", "dockerhub")
                .description("Number of Docker Hub fetches")
                .register(meterRegistry);

        this.localCacheLatency = Timer.builder("station.cache.latency")
                .tag("level", "local")
                .description("Local cache operation latency")
                .register(meterRegistry);

        this.redisCacheLatency = Timer.builder("station.cache.latency")
                .tag("level", "redis")
                .description("Redis cache operation latency")
                .register(meterRegistry);

        this.peerCacheLatency = Timer.builder("station.cache.latency")
                .tag("level", "peer")
                .description("Peer cache operation latency")
                .register(meterRegistry);

        this.dockerHubLatency = Timer.builder("station.cache.latency")
                .tag("level", "dockerhub")
                .description("Docker Hub fetch latency")
                .register(meterRegistry);
    }

    public void recordLocalCacheHit() {
        localCacheHits.increment();
    }

    public void recordLocalCacheMiss() {
        localCacheMisses.increment();
    }

    public void recordRedisCacheHit() {
        redisCacheHits.increment();
    }

    public void recordRedisCacheMiss() {
        redisCacheMisses.increment();
    }

    public void recordPeerCacheHit() {
        peerCacheHits.increment();
    }

    public void recordPeerCacheMiss() {
        peerCacheMisses.increment();
    }

    public void recordDockerHubFetch() {
        dockerHubHits.increment();
    }

    public Timer.Sample startLocalCacheTimer() {
        return Timer.start();
    }

    public void recordLocalCacheLatency(Timer.Sample sample) {
        sample.stop(localCacheLatency);
    }

    public Timer.Sample startRedisCacheTimer() {
        return Timer.start();
    }

    public void recordRedisCacheLatency(Timer.Sample sample) {
        sample.stop(redisCacheLatency);
    }

    public Timer.Sample startPeerCacheTimer() {
        return Timer.start();
    }

    public void recordPeerCacheLatency(Timer.Sample sample) {
        sample.stop(peerCacheLatency);
    }

    public Timer.Sample startDockerHubTimer() {
        return Timer.start();
    }

    public void recordDockerHubLatency(Timer.Sample sample) {
        sample.stop(dockerHubLatency);
    }
}
