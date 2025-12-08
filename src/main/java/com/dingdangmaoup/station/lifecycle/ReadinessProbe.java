package com.dingdangmaoup.station.lifecycle;

import com.dingdangmaoup.station.storage.BlobStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.ReactiveHealthIndicator;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Custom readiness probe for Kubernetes
 * Implements ReactiveHealthIndicator for Spring Boot Actuator integration
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReadinessProbe implements ReactiveHealthIndicator {

    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;
    private final BlobStorage blobStorage;

    private volatile boolean draining = false;

    /**
     * Spring Boot Actuator health check method
     */
    @Override
    public Mono<Health> health() {
        return readiness()
                .map(response -> {
                    String status = (String) response.get("status");
                    if ("UP".equals(status)) {
                        return Health.up()
                                .withDetails(response)
                                .build();
                    } else {
                        return Health.down()
                                .withDetails(response)
                                .build();
                    }
                })
                .onErrorResume(error -> {
                    log.error("Health check error", error);
                    return Mono.just(Health.down()
                            .withException(error)
                            .build());
                });
    }

    public Mono<Map<String, Object>> readiness() {
        // If draining, fail readiness immediately
        if (draining) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "DOWN");
            response.put("reason", "draining");
            return Mono.just(response);
        }

        // Check Redis connection
        Mono<Boolean> redisCheck = reactiveRedisTemplate.execute(connection ->
                        connection.ping())
                .next()
                .map(response -> "PONG".equals(response))
                .timeout(Duration.ofSeconds(2))
                .onErrorReturn(false);

        // Check storage availability
        Mono<Boolean> storageCheck = blobStorage.getAvailableSpace()
                .map(space -> space > 1024 * 1024 * 1024) // At least 1GB free
                .timeout(Duration.ofSeconds(2))
                .onErrorReturn(false);

        return Mono.zip(redisCheck, storageCheck)
                .map(tuple -> {
                    boolean redisHealthy = tuple.getT1();
                    boolean storageHealthy = tuple.getT2();

                    Map<String, Object> response = new HashMap<>();

                    if (redisHealthy && storageHealthy) {
                        response.put("status", "UP");
                        response.put("redis", "connected");
                        response.put("storage", "available");
                    } else {
                        response.put("status", "DOWN");
                        if (!redisHealthy) {
                            response.put("redis", "disconnected");
                        }
                        if (!storageHealthy) {
                            response.put("storage", "insufficient space");
                        }
                    }
                    return response;
                })
                .onErrorResume(error -> {
                    log.error("Readiness check error", error);
                    Map<String, Object> response = new HashMap<>();
                    response.put("status", "DOWN");
                    response.put("error", error.getMessage());
                    return Mono.just(response);
                });
    }

    public void setDraining(boolean draining) {
        this.draining = draining;
        log.info("Readiness probe draining status set to: {}", draining);
    }
}
