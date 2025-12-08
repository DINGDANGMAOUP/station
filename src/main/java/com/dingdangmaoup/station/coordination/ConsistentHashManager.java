package com.dingdangmaoup.station.coordination;

import com.dingdangmaoup.station.config.properties.CoordinationProperties;
import com.dingdangmaoup.station.node.discovery.NodeInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.List;

/**
 * Manages consistent hash ring for node distribution
 */
@Slf4j
@Component
public class ConsistentHashManager {

    private final ConsistentHash<NodeInfo> hashRing;
    private final int virtualNodes;

    public ConsistentHashManager(CoordinationProperties coordinationProperties) {
        this.virtualNodes = coordinationProperties.getConsistentHash().getVirtualNodes();
        this.hashRing = new ConsistentHash<>(virtualNodes);
    }

    @PostConstruct
    public void init() {
        log.info("Initialized ConsistentHashManager with {} virtual nodes per physical node", virtualNodes);
    }

    /**
     * Add a node to the hash ring
     */
    public void addNode(NodeInfo node) {
        hashRing.addNode(node);
        log.info("Added node to hash ring: {} (total nodes: {})", node.getNodeId(), hashRing.size());
    }

    /**
     * Remove a node from the hash ring
     */
    public void removeNode(NodeInfo node) {
        hashRing.removeNode(node);
        log.info("Removed node from hash ring: {} (total nodes: {})", node.getNodeId(), hashRing.size());
    }

    /**
     * Get the primary node responsible for a given key
     */
    public NodeInfo getNodeForKey(String key) {
        NodeInfo node = hashRing.getNode(key);
        if (node != null) {
            log.debug("Key '{}' maps to node: {}", key, node.getNodeId());
        } else {
            log.warn("No node found for key '{}' (hash ring empty)", key);
        }
        return node;
    }

    /**
     * Get N nodes for a given key (for replication or fallback)
     */
    public List<NodeInfo> getNodesForKey(String key, int count) {
        List<NodeInfo> nodes = hashRing.getNodes(key, count);
        log.debug("Key '{}' maps to {} nodes: {}", key, nodes.size(),
                nodes.stream().map(NodeInfo::getNodeId).toList());
        return nodes;
    }

    /**
     * Get all nodes in the ring
     */
    public List<NodeInfo> getAllNodes() {
        return List.copyOf(hashRing.getAllNodes());
    }

    /**
     * Get the size of the hash ring (number of physical nodes)
     */
    public int getNodeCount() {
        return hashRing.size();
    }

    /**
     * Check if the hash ring is empty
     */
    public boolean isEmpty() {
        return hashRing.size() == 0;
    }

    /**
     * Update the hash ring with a new set of nodes
     */
    public void updateNodes(List<NodeInfo> nodes) {
        List<NodeInfo> currentNodes = getAllNodes();
        for (NodeInfo currentNode : currentNodes) {
            if (!nodes.contains(currentNode)) {
                removeNode(currentNode);
            }
        }

        for (NodeInfo node : nodes) {
            if (!currentNodes.contains(node)) {
                addNode(node);
            }
        }

        log.info("Updated hash ring: {} nodes active", hashRing.size());
    }
}
