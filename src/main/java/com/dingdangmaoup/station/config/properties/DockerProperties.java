package com.dingdangmaoup.station.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Docker Hub configuration properties
 */
@Data
@Component
@ConfigurationProperties(prefix = "station.docker")
public class DockerProperties {

    /**
     * Docker Hub registry URL
     */
    private String hubUrl = "https://registry-1.docker.io";

    /**
     * Docker Hub authentication URL
     */
    private String authUrl = "https://auth.docker.io";

    /**
     * Request timeout
     */
    private Duration timeout = Duration.ofSeconds(30);

    /**
     * Maximum concurrent downloads
     */
    private int maxConcurrentDownloads = 10;

    /**
     * Retry configuration
     */
    private Retry retry = new Retry();

    @Data
    public static class Retry {
        /**
         * Maximum retry attempts
         */
        private int maxAttempts = 3;

        /**
         * Backoff delay between retries
         */
        private Duration backoffDelay = Duration.ofSeconds(1);
    }
}
