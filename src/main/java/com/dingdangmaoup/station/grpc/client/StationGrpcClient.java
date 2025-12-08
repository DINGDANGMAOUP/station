package com.dingdangmaoup.station.grpc.client;

import com.dingdangmaoup.station.grpc.*;
import com.dingdangmaoup.station.node.discovery.NodeInfo;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * gRPC client for communicating with peer nodes
 */
@Slf4j
@Component
public class StationGrpcClient {

    private final Map<String, ManagedChannel> channelCache = new ConcurrentHashMap<>();
    private final Map<String, ReactorStationServiceGrpc.ReactorStationServiceStub> stubCache = new ConcurrentHashMap<>();
    private final DefaultDataBufferFactory bufferFactory = new DefaultDataBufferFactory();

    /**
     * Check if a peer node has a manifest
     */
    public Mono<Boolean> hasManifest(NodeInfo node, String repository, String reference) {
        return getStub(node)
                .flatMap(stub -> {
                    ManifestRequest request = ManifestRequest.newBuilder()
                            .setRepository(repository)
                            .setReference(reference)
                            .build();

                    return stub.hasManifest(Mono.just(request))
                            .map(ManifestResponse::getExists)
                            .timeout(Duration.ofSeconds(5))
                            .doOnError(error -> log.error("Error checking manifest on node {}: {}",
                                    node.getNodeId(), error.getMessage()));
                })
                .onErrorReturn(false);
    }

    /**
     * Get manifest from a peer node
     */
    public Mono<ManifestData> getManifest(NodeInfo node, String repository, String reference) {
        return getStub(node)
                .flatMapMany(stub -> {
                    ManifestRequest request = ManifestRequest.newBuilder()
                            .setRepository(repository)
                            .setReference(reference)
                            .build();

                    return stub.getManifest(Mono.just(request))
                            .timeout(Duration.ofSeconds(30));
                })
                .next()
                .doOnSuccess(data -> log.debug("Retrieved manifest {}:{} from node {}",
                        repository, reference, node.getNodeId()))
                .doOnError(error -> log.error("Error getting manifest from node {}: {}",
                        node.getNodeId(), error.getMessage()));
    }

    /**
     * Check if a peer node has a blob
     */
    public Mono<Boolean> hasBlob(NodeInfo node, String digest) {
        return getStub(node)
                .flatMap(stub -> {
                    BlobRequest request = BlobRequest.newBuilder()
                            .setDigest(digest)
                            .build();

                    return stub.hasBlob(Mono.just(request))
                            .map(BlobResponse::getExists)
                            .timeout(Duration.ofSeconds(5))
                            .doOnError(error -> log.error("Error checking blob on node {}: {}",
                                    node.getNodeId(), error.getMessage()));
                })
                .onErrorReturn(false);
    }

    /**
     * Stream blob from a peer node
     */
    public Flux<DataBuffer> getBlob(NodeInfo node, String digest) {
        return getStub(node)
                .flatMapMany(stub -> {
                    BlobRequest request = BlobRequest.newBuilder()
                            .setDigest(digest)
                            .build();

                    return stub.getBlob(Mono.just(request))
                            .map(blobData -> {
                                byte[] bytes = blobData.getChunk().toByteArray();
                                DataBuffer buffer = bufferFactory.wrap(bytes);
                                return buffer;
                            })
                            .timeout(Duration.ofMinutes(5))
                            .doOnComplete(() -> log.debug("Completed streaming blob {} from node {}",
                                    digest, node.getNodeId()));
                })
                .doOnError(error -> log.error("Error streaming blob from node {}: {}",
                        node.getNodeId(), error.getMessage()));
    }

    /**
     * Health check on a peer node
     */
    public Mono<HealthCheckResponse> healthCheck(NodeInfo node) {
        return getStub(node)
                .flatMap(stub -> {
                    HealthCheckRequest request = HealthCheckRequest.newBuilder().build();

                    return stub.healthCheck(Mono.just(request))
                            .timeout(Duration.ofSeconds(3))
                            .doOnError(error -> log.warn("Health check failed for node {}: {}",
                                    node.getNodeId(), error.getMessage()));
                })
                .onErrorReturn(HealthCheckResponse.newBuilder()
                        .setStatus(HealthCheckResponse.Status.UNHEALTHY)
                        .build());
    }

    /**
     * Get node info from a peer
     */
    public Mono<NodeInfoResponse> getNodeInfo(NodeInfo node) {
        return getStub(node)
                .flatMap(stub -> {
                    NodeInfoRequest request = NodeInfoRequest.newBuilder().build();

                    return stub.getNodeInfo(Mono.just(request))
                            .timeout(Duration.ofSeconds(3));
                })
                .doOnError(error -> log.error("Error getting node info from {}: {}",
                        node.getNodeId(), error.getMessage()));
    }

    /**
     * Get or create gRPC stub for a node
     */
    private Mono<ReactorStationServiceGrpc.ReactorStationServiceStub> getStub(NodeInfo node) {
        return Mono.fromCallable(() -> {
            String nodeKey = node.getNodeId();

            return stubCache.computeIfAbsent(nodeKey, k -> {
                ManagedChannel channel = getOrCreateChannel(node);
                log.debug("Created gRPC stub for node: {}", node);
                return ReactorStationServiceGrpc.newReactorStub(channel);
            });
        });
    }

    /**
     * Get or create gRPC channel for a node
     */
    private ManagedChannel getOrCreateChannel(NodeInfo node) {
        String nodeKey = node.getNodeId();

        return channelCache.computeIfAbsent(nodeKey, k -> {
            log.info("Creating gRPC channel to node: {} ({})", node.getNodeId(), node.getGrpcAddress());

            return ManagedChannelBuilder.forTarget(node.getGrpcAddress())
                    .usePlaintext()
                    .keepAliveTime(30, TimeUnit.SECONDS)
                    .keepAliveTimeout(10, TimeUnit.SECONDS)
                    .build();
        });
    }

    /**
     * Close channel for a node
     */
    public void closeChannel(String nodeId) {
        ManagedChannel channel = channelCache.remove(nodeId);
        if (channel != null) {
            try {
                channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
                log.info("Closed gRPC channel for node: {}", nodeId);
            } catch (InterruptedException e) {
                log.warn("Interrupted while closing channel for node: {}", nodeId);
                Thread.currentThread().interrupt();
            }
        }
        stubCache.remove(nodeId);
    }

    /**
     * Close all channels
     */
    public void closeAllChannels() {
        log.info("Closing all gRPC channels");
        channelCache.keySet().forEach(this::closeChannel);
    }
}
