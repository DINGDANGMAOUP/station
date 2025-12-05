package com.dingdangmaoup.station.node.discovery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import reactor.util.retry.Retry;

/**
 * Redis-based node discovery implementation
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "station.discovery.type", havingValue = "redis", matchIfMissing = true)
public class RedisNodeDiscovery implements NodeDiscoveryService {

    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${station.node.id}")
    private String nodeId;

    @Value("${station.node.grpc-port:50051}")
    private int grpcPort;

    @Value("${station.node.http-port:5000}")
    private int httpPort;

    @Value("${station.discovery.redis.node-timeout:30s}")
    private Duration nodeTimeout;

    @Value("${station.discovery.redis.max-attempts:6}")
    private long maxRetryAttempts;

    @Value("${station.discovery.redis.initial-backoff:5s}")
    private Duration initialBackoff;

    @Value("${station.discovery.redis.max-backoff:10s}")
    private Duration maxBackoff;

    private volatile NodeInfo.NodeStatus currentStatus = NodeInfo.NodeStatus.HEALTHY;
    private final Instant startTime = Instant.now();

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        registerSelf()
                .retryWhen(Retry.backoff(maxRetryAttempts, initialBackoff)
                        .maxBackoff(maxBackoff)
                        .doBeforeRetry(signal ->
                            log.warn("Retrying node registration, attempt: {}", signal.totalRetries() + 1)))
                .doOnSuccess(v -> log.info("Node registration successful"))
                .doOnError(e -> log.error("Failed to register node after retries", e))
                .subscribe();
    }

    @Override
    public Mono<Void> registerSelf() {
        NodeInfo nodeInfo = buildNodeInfo();
        return saveNodeInfo(nodeInfo)
                .then(reactiveRedisTemplate.opsForSet().add("nodes:active", nodeId))
                .doOnSuccess(count -> log.info("Registered node: {}", nodeInfo))
                .then();
    }

    @Override
    public Mono<Void> deregister() {
        return reactiveRedisTemplate.delete("node:" + nodeId)
                .then(reactiveRedisTemplate.opsForSet().remove("nodes:active", nodeId))
                .doOnSuccess(count -> log.info("Deregistered node: {}", nodeId))
                .then();
    }

    @Override
    public Mono<Void> heartbeat() {
        if (currentStatus == NodeInfo.NodeStatus.DRAINING) {
            log.debug("Skipping heartbeat, node is draining");
            return Mono.empty();
        }

        NodeInfo nodeInfo = buildNodeInfo();
        return saveNodeInfo(nodeInfo)
                .doOnSuccess(success -> log.trace("Heartbeat sent for node: {}", nodeId))
                .then();
    }

    /**
     * Scheduled heartbeat task that properly subscribes to the reactive heartbeat
     */
    @Scheduled(fixedDelayString = "${station.discovery.heartbeat-interval:10s}")
    public void scheduledHeartbeat() {
        heartbeat()
                .doOnError(e -> log.error("Failed to send heartbeat", e))
                .subscribe();
    }

    @Override
    public Flux<NodeInfo> discoverNodes() {
        return reactiveRedisTemplate.opsForSet()
                .members("nodes:active")
                .flatMap(this::getNode)
                .filter(node -> !node.getNodeId().equals(nodeId)); // Exclude self
    }

    @Override
    public Mono<NodeInfo> getNode(String targetNodeId) {
        return reactiveRedisTemplate.opsForValue()
                .get("node:" + targetNodeId)
                .flatMap(json -> {
                    try {
                        NodeInfo info = objectMapper.readValue(json, NodeInfo.class);

                        // Check if node is expired
                        if (info.getLastHeartbeat().plus(nodeTimeout).isBefore(Instant.now())) {
                            log.warn("Node {} is expired, removing from active set", targetNodeId);
                            return reactiveRedisTemplate.opsForSet()
                                    .remove("nodes:active", targetNodeId)
                                    .then(Mono.empty());
                        }

                        return Mono.just(info);
                    } catch (JsonProcessingException e) {
                        log.error("Failed to deserialize node info for: {}", targetNodeId, e);
                        return Mono.empty();
                    }
                });
    }

    @Override
    public Mono<Void> markDraining() {
        currentStatus = NodeInfo.NodeStatus.DRAINING;
        NodeInfo nodeInfo = buildNodeInfo();
        return saveNodeInfo(nodeInfo)
                .doOnSuccess(success -> log.info("Marked node as draining: {}", nodeId))
                .then();
    }

    private NodeInfo buildNodeInfo() {
        return NodeInfo.builder()
                .nodeId(nodeId)
                .host(getHostAddress())
                .grpcPort(grpcPort)
                .httpPort(httpPort)
                .status(currentStatus)
                .lastHeartbeat(Instant.now())
                .uptimeSeconds(Duration.between(startTime, Instant.now()).getSeconds())
                .build();
    }

    private Mono<Boolean> saveNodeInfo(NodeInfo nodeInfo) {
        try {
            String json = objectMapper.writeValueAsString(nodeInfo);
            return reactiveRedisTemplate.opsForValue()
                    .set("node:" + nodeId, json, nodeTimeout.plusSeconds(10));
        } catch (JsonProcessingException e) {
            return Mono.error(new RuntimeException("Failed to serialize node info", e));
        }
    }

    private String getHostAddress() {
        // Try to get hostname from environment (K8s pod name)
        String hostname = System.getenv("HOSTNAME");
        if (hostname != null && !hostname.isBlank()) {
            return hostname;
        }

        // Fallback to localhost
        return "localhost";
    }
}
