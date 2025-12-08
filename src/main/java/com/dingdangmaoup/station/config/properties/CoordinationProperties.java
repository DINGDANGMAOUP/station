package com.dingdangmaoup.station.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Data
@Component
@ConfigurationProperties(prefix = "station.coordination")
public class CoordinationProperties {

    private ConsistentHashConfig consistentHash = new ConsistentHashConfig();
    private LockConfig lock = new LockConfig();

    @Data
    public static class ConsistentHashConfig {
        private int virtualNodes = 150;
    }

    @Data
    public static class LockConfig {
        private Duration ttl = Duration.ofSeconds(30);
        private Duration waitTimeout = Duration.ofSeconds(10);
    }
}
