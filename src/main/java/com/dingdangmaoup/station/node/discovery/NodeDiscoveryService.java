package com.dingdangmaoup.station.node.discovery;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Node discovery service interface
 */
public interface NodeDiscoveryService {

    /**
     * Register current node
     */
    Mono<Void> registerSelf();

    /**
     * Deregister current node
     */
    Mono<Void> deregister();

    /**
     * Send heartbeat
     */
    Mono<Void> heartbeat();

    /**
     * Discover all active nodes
     */
    Flux<NodeInfo> discoverNodes();

    /**
     * Get specific node by ID
     */
    Mono<NodeInfo> getNode(String nodeId);

    /**
     * Mark node as draining
     */
    Mono<Void> markDraining();
}
