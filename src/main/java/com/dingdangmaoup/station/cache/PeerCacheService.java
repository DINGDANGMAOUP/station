package com.dingdangmaoup.station.cache;

import com.dingdangmaoup.station.coordination.ConsistentHashManager;
import com.dingdangmaoup.station.grpc.ManifestData;
import com.dingdangmaoup.station.grpc.client.StationGrpcClient;
import com.dingdangmaoup.station.metrics.CacheMetrics;
import com.dingdangmaoup.station.node.discovery.NodeDiscoveryService;
import com.dingdangmaoup.station.node.discovery.NodeInfo;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

/**
 * Service for querying peer nodes using consistent hashing
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PeerCacheService {

    private final ConsistentHashManager consistentHashManager;
    private final NodeDiscoveryService nodeDiscoveryService;
    private final StationGrpcClient grpcClient;
    private final CacheMetrics cacheMetrics;

    @Value("${station.node.id}")
    private String currentNodeId;

    /**
     * Refresh peer nodes on application startup
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        scheduledRefreshPeerNodes();
    }

    /**
     * Periodically refresh the list of peer nodes and update the consistent hash ring
     */
    @Scheduled(fixedDelayString = "${station.discovery.refresh-interval:5s}")
    public void scheduledRefreshPeerNodes() {
        nodeDiscoveryService.discoverNodes()
                .collectList()
                .doOnNext(allNodes -> {
                    consistentHashManager.updateNodes(allNodes);
                    log.debug("Refreshed peer nodes: {} nodes discovered", allNodes.size());
                })
                .doOnError(e -> log.error("Failed to refresh peer nodes", e))
                .subscribe();
    }

    /**
     * Query peer nodes for a manifest using consistent hashing
     */
    public Mono<Optional<ManifestData>> queryPeersForManifest(String repository, String reference) {
        String key = String.format("manifest:%s:%s", repository, reference);

        Timer.Sample peerTimer = cacheMetrics.startPeerCacheTimer();

        return getPeerNodesForKey(key)
                .flatMap(peers -> {
                    if (peers.isEmpty()) {
                        log.debug("No peer nodes available for manifest query");
                        return Mono.just(Optional.<ManifestData>empty());
                    }

                    log.debug("Querying {} peer nodes for manifest {}:{}", peers.size(), repository, reference);

                    // Try each peer in order (consistent hash order)
                    return tryPeersSequentially(peers, node ->
                        grpcClient.hasManifest(node, repository, reference)
                                .flatMap(exists -> {
                                    if (exists) {
                                        return grpcClient.getManifest(node, repository, reference)
                                                .map(Optional::of);
                                    }
                                    return Mono.just(Optional.<ManifestData>empty());
                                })
                    );
                })
                .doOnNext(result -> {
                    cacheMetrics.recordPeerCacheLatency(peerTimer);
                    if (result.isPresent()) {
                        log.info("Cache HIT at L3 (Peer): manifest {}:{}", repository, reference);
                        cacheMetrics.recordPeerCacheHit();
                    } else {
                        log.debug("Cache MISS at L3 (Peer): manifest {}:{}", repository, reference);
                        cacheMetrics.recordPeerCacheMiss();
                    }
                })
                .onErrorResume(error -> {
                    log.error("Error querying peers for manifest", error);
                    cacheMetrics.recordPeerCacheMiss();
                    return Mono.just(Optional.empty());
                });
    }

    /**
     * Query peer nodes for a blob using consistent hashing
     */
    public Mono<Optional<Flux<DataBuffer>>> queryPeersForBlob(String digest) {
        String key = String.format("blob:%s", digest);

        Timer.Sample peerTimer = cacheMetrics.startPeerCacheTimer();

        return getPeerNodesForKey(key)
                .flatMap(peers -> {
                    if (peers.isEmpty()) {
                        log.debug("No peer nodes available for blob query");
                        return Mono.just(Optional.<Flux<DataBuffer>>empty());
                    }

                    log.debug("Querying {} peer nodes for blob {}", peers.size(), digest);

                    // Try each peer in order
                    return tryPeersSequentiallyForBlob(peers, node ->
                        grpcClient.hasBlob(node, digest)
                                .flatMap(exists -> {
                                    if (exists) {
                                        Flux<DataBuffer> blobStream = grpcClient.getBlob(node, digest);
                                        return Mono.just(Optional.of(blobStream));
                                    }
                                    return Mono.just(Optional.<Flux<DataBuffer>>empty());
                                })
                    );
                })
                .doOnNext(result -> {
                    cacheMetrics.recordPeerCacheLatency(peerTimer);
                    if (result.isPresent()) {
                        log.info("Cache HIT at L3 (Peer): blob {}", digest);
                        cacheMetrics.recordPeerCacheHit();
                    } else {
                        log.debug("Cache MISS at L3 (Peer): blob {}", digest);
                        cacheMetrics.recordPeerCacheMiss();
                    }
                })
                .onErrorResume(error -> {
                    log.error("Error querying peers for blob", error);
                    cacheMetrics.recordPeerCacheMiss();
                    return Mono.just(Optional.empty());
                });
    }

    /**
     * Get peer nodes for a given key using consistent hashing
     * Uses the cached hash ring (refreshed periodically in background)
     * Excludes the current node
     */
    private Mono<List<NodeInfo>> getPeerNodesForKey(String key) {
        return Mono.fromSupplier(() -> {
            if (consistentHashManager.isEmpty()) {
                log.warn("Consistent hash ring is empty");
                return List.<NodeInfo>of();
            }

            // Get nodes for this key (ordered by consistent hash)
            List<NodeInfo> candidateNodes = consistentHashManager.getNodesForKey(key, 3);

            // Filter out current node
            List<NodeInfo> peerNodes = candidateNodes.stream()
                    .filter(node -> !node.getNodeId().equals(currentNodeId))
                    .toList();

            log.debug("Consistent hash for key '{}' returned {} peer nodes", key, peerNodes.size());
            return peerNodes;
        });
    }

    /**
     * Try peers sequentially until one succeeds
     */
    private <T> Mono<Optional<T>> tryPeersSequentially(List<NodeInfo> peers,
                                                        java.util.function.Function<NodeInfo, Mono<Optional<T>>> operation) {
        if (peers.isEmpty()) {
            return Mono.just(Optional.empty());
        }

        NodeInfo firstPeer = peers.get(0);
        List<NodeInfo> remainingPeers = peers.subList(1, peers.size());

        return operation.apply(firstPeer)
                .flatMap(result -> {
                    if (result.isPresent()) {
                        return Mono.just(result);
                    }
                    // Try next peer
                    return tryPeersSequentially(remainingPeers, operation);
                });
    }

    /**
     * Try peers sequentially for blob streaming
     */
    private Mono<Optional<Flux<DataBuffer>>> tryPeersSequentiallyForBlob(
            List<NodeInfo> peers,
            java.util.function.Function<NodeInfo, Mono<Optional<Flux<DataBuffer>>>> operation) {
        if (peers.isEmpty()) {
            return Mono.just(Optional.empty());
        }

        NodeInfo firstPeer = peers.get(0);
        List<NodeInfo> remainingPeers = peers.subList(1, peers.size());

        return operation.apply(firstPeer)
                .flatMap(result -> {
                    if (result.isPresent()) {
                        return Mono.just(result);
                    }
                    // Try next peer
                    return tryPeersSequentiallyForBlob(remainingPeers, operation);
                });
    }
}
