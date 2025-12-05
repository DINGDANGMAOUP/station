package com.dingdangmaoup.station.coordination;

import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Consistent hashing implementation with virtual nodes
 */
@Slf4j
public class ConsistentHash<T> {

    private final int numberOfVirtualNodes;
    private final ConcurrentSkipListMap<Long, T> ring = new ConcurrentSkipListMap<>();
    private final Map<T, List<Long>> nodeHashes = new HashMap<>();

    public ConsistentHash(int numberOfVirtualNodes) {
        this.numberOfVirtualNodes = numberOfVirtualNodes;
    }

    /**
     * Add a node to the hash ring
     */
    public void addNode(T node) {
        List<Long> hashes = new ArrayList<>();
        for (int i = 0; i < numberOfVirtualNodes; i++) {
            String virtualNodeKey = node.toString() + "#" + i;
            long hash = hash(virtualNodeKey);
            ring.put(hash, node);
            hashes.add(hash);
        }
        nodeHashes.put(node, hashes);
        log.debug("Added node {} with {} virtual nodes", node, numberOfVirtualNodes);
    }

    /**
     * Remove a node from the hash ring
     */
    public void removeNode(T node) {
        List<Long> hashes = nodeHashes.remove(node);
        if (hashes != null) {
            for (Long hash : hashes) {
                ring.remove(hash);
            }
            log.debug("Removed node {} ({} virtual nodes)", node, hashes.size());
        }
    }

    /**
     * Get the node for a given key
     */
    public T getNode(String key) {
        if (ring.isEmpty()) {
            return null;
        }

        long hash = hash(key);
        Map.Entry<Long, T> entry = ring.ceilingEntry(hash);

        if (entry == null) {
            // Wrap around to the first node
            entry = ring.firstEntry();
        }

        return entry.getValue();
    }

    /**
     * Get N nodes for a given key (for replication)
     */
    public List<T> getNodes(String key, int count) {
        if (ring.isEmpty()) {
            return Collections.emptyList();
        }

        Set<T> selectedNodes = new LinkedHashSet<>();
        long hash = hash(key);

        // Get nodes starting from the hash position
        Iterator<Map.Entry<Long, T>> iterator = ring.tailMap(hash).entrySet().iterator();

        while (selectedNodes.size() < count && iterator.hasNext()) {
            selectedNodes.add(iterator.next().getValue());
        }

        if (selectedNodes.size() < count) {
            iterator = ring.headMap(hash).entrySet().iterator();
            while (selectedNodes.size() < count && iterator.hasNext()) {
                selectedNodes.add(iterator.next().getValue());
            }
        }

        return new ArrayList<>(selectedNodes);
    }

    /**
     * Get all nodes in the ring
     */
    public Set<T> getAllNodes() {
        return new HashSet<>(nodeHashes.keySet());
    }

    /**
     * Get the size of the ring (number of physical nodes)
     */
    public int size() {
        return nodeHashes.size();
    }

    /**
     * Hash function using MD5
     */
    private long hash(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(key.getBytes(StandardCharsets.UTF_8));

            long hash = 0;
            for (int i = 0; i < 8; i++) {
                hash = (hash << 8) | (digest[i] & 0xFF);
            }
            return hash;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not found", e);
        }
    }
}
