package com.dingdangmaoup.station.node.discovery;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class NodeInfo {
    @EqualsAndHashCode.Include
    private String nodeId;
    private String host;
    private int grpcPort;
    private int httpPort;
    private NodeStatus status;
    private Instant lastHeartbeat;
    private long uptimeSeconds;

    public enum NodeStatus {
        HEALTHY,
        UNHEALTHY,
        DRAINING
    }

    @JsonIgnore
    public String getGrpcAddress() {
        return host + ":" + grpcPort;
    }

    @JsonIgnore
    public String getHttpAddress() {
        return host + ":" + httpPort;
    }

    @Override
    public String toString() {
        return String.format("Node[id=%s, grpc=%s:%d, status=%s]",
                nodeId, host, grpcPort, status);
    }
}
