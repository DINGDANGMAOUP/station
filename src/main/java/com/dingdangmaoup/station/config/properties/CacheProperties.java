package com.dingdangmaoup.station.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

import java.time.Duration;

/**
 * Cache configuration properties
 */
@Data
@Component
@ConfigurationProperties(prefix = "station.cache")
public class CacheProperties {

    /**
     * Local cache configuration (Caffeine)
     */
    private Local local = new Local();

    /**
     * Redis cache configuration
     */
    private Redis redis = new Redis();

    @Data
    public static class Local {
        /**
         * Maximum cache size in bytes
         */
        private DataSize maxSize = DataSize.ofGigabytes(1);

        /**
         * Maximum number of cache entries
         */
        private int maxEntries = 10000;

        /**
         * Time to live for cache entries
         */
        private Duration ttl = Duration.ofHours(1);
    }

    @Data
    public static class Redis {
        /**
         * TTL for manifest entries
         */
        private Duration manifestTtl = Duration.ofHours(24);

        /**
         * TTL for blob entries
         */
        private Duration blobTtl = Duration.ofHours(168); // 7 days

        /**
         * TTL for index entries (not currently used)
         */
        private Duration indexTtl = Duration.ofHours(1);
    }
}
