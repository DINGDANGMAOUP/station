package com.dingdangmaoup.station.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "station.redis")
public class RedisProperties {

    private Mode mode = Mode.STANDALONE;
    private String host = "localhost";
    private int port = 6379;
    private String password;
    private int database = 0;
    private Duration timeout = Duration.ofSeconds(5);

    // Cluster configuration
    private ClusterConfig cluster = new ClusterConfig();

    // Sentinel configuration
    private SentinelConfig sentinel = new SentinelConfig();

    public enum Mode {
        STANDALONE,
        CLUSTER,
        SENTINEL
    }

    @Data
    public static class ClusterConfig {
        private String nodes;  // Support comma-separated string
        private int maxRedirects = 3;

        // Get nodes as list, supporting both comma-separated string and list
        public List<String> getNodesList() {
            if (nodes == null || nodes.isBlank()) {
                return List.of();
            }
            return Arrays.stream(nodes.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }
    }

    @Data
    public static class SentinelConfig {
        private String master = "mymaster";
        private String nodes;  // Support comma-separated string

        // Get nodes as list, supporting both comma-separated string and list
        public List<String> getNodesList() {
            if (nodes == null || nodes.isBlank()) {
                return List.of();
            }
            return Arrays.stream(nodes.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }
    }
}
