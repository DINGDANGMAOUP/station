package com.dingdangmaoup.station.node.discovery;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test NodeInfo equals/hashCode behavior for consistent hashing
 */
class NodeInfoTest {

    @Test
    void testEquals_sameNodeId_differentTimestamp() {
        // Same node at different times should be equal
        NodeInfo node1 = NodeInfo.builder()
                .nodeId("node-1")
                .host("192.168.1.10")
                .grpcPort(50051)
                .httpPort(5000)
                .status(NodeInfo.NodeStatus.HEALTHY)
                .lastHeartbeat(Instant.now())
                .uptimeSeconds(100)
                .build();

        NodeInfo node2 = NodeInfo.builder()
                .nodeId("node-1")
                .host("192.168.1.10")
                .grpcPort(50051)
                .httpPort(5000)
                .status(NodeInfo.NodeStatus.HEALTHY)
                .lastHeartbeat(Instant.now().plusSeconds(60))  // Different timestamp
                .uptimeSeconds(160)  // Different uptime
                .build();

        assertEquals(node1, node2, "Nodes with same nodeId should be equal");
        assertEquals(node1.hashCode(), node2.hashCode(), "Nodes with same nodeId should have same hashCode");
    }

    @Test
    void testEquals_sameNodeId_differentStatus() {
        // Same node with different status should be equal (for consistent hashing)
        NodeInfo node1 = NodeInfo.builder()
                .nodeId("node-1")
                .host("192.168.1.10")
                .grpcPort(50051)
                .httpPort(5000)
                .status(NodeInfo.NodeStatus.HEALTHY)
                .lastHeartbeat(Instant.now())
                .uptimeSeconds(100)
                .build();

        NodeInfo node2 = NodeInfo.builder()
                .nodeId("node-1")
                .host("192.168.1.10")
                .grpcPort(50051)
                .httpPort(5000)
                .status(NodeInfo.NodeStatus.DRAINING)  // Different status
                .lastHeartbeat(Instant.now())
                .uptimeSeconds(100)
                .build();

        assertEquals(node1, node2, "Nodes with same nodeId but different status should be equal");
        assertEquals(node1.hashCode(), node2.hashCode());
    }

    @Test
    void testEquals_differentNodeId() {
        NodeInfo node1 = NodeInfo.builder()
                .nodeId("node-1")
                .host("192.168.1.10")
                .grpcPort(50051)
                .httpPort(5000)
                .status(NodeInfo.NodeStatus.HEALTHY)
                .lastHeartbeat(Instant.now())
                .uptimeSeconds(100)
                .build();

        NodeInfo node2 = NodeInfo.builder()
                .nodeId("node-2")  // Different nodeId
                .host("192.168.1.10")  // Same host
                .grpcPort(50051)
                .httpPort(5000)
                .status(NodeInfo.NodeStatus.HEALTHY)
                .lastHeartbeat(Instant.now())
                .uptimeSeconds(100)
                .build();

        assertNotEquals(node1, node2, "Nodes with different nodeId should not be equal");
    }

    @Test
    void testContains_listBehavior() {
        NodeInfo node1 = NodeInfo.builder()
                .nodeId("node-1")
                .host("192.168.1.10")
                .grpcPort(50051)
                .httpPort(5000)
                .status(NodeInfo.NodeStatus.HEALTHY)
                .lastHeartbeat(Instant.parse("2025-01-01T00:00:00Z"))
                .uptimeSeconds(100)
                .build();

        NodeInfo node2 = NodeInfo.builder()
                .nodeId("node-1")
                .host("192.168.1.10")
                .grpcPort(50051)
                .httpPort(5000)
                .status(NodeInfo.NodeStatus.HEALTHY)
                .lastHeartbeat(Instant.parse("2025-01-01T01:00:00Z"))  // 1 hour later
                .uptimeSeconds(3700)  // 1 hour + 100 seconds
                .build();

        var nodes = java.util.List.of(node1);

        // This should return true because equals only checks nodeId
        assertTrue(nodes.contains(node2),
                "List.contains() should return true for node with same nodeId but different timestamp");
    }
}
