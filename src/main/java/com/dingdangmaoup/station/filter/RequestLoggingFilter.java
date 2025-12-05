package com.dingdangmaoup.station.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Request logging filter to debug Docker client requests
 * Enable by setting: station.logging.request-logging=true
 */
@Slf4j
@Component
@Order(-1)
@ConditionalOnProperty(name = "station.logging.request-logging", havingValue = "true", matchIfMissing = false)
public class RequestLoggingFilter implements WebFilter {

    @Value("${station.logging.include-headers:false}")
    private boolean includeHeaders;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!log.isDebugEnabled()) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest();

        log.debug("=== Incoming Request ===");
        log.debug("Method: {}", request.getMethod());
        log.debug("Path: {}", request.getPath().value());
        log.debug("URI: {}", request.getURI());
        log.debug("Query: {}", request.getURI().getQuery());

        if (includeHeaders) {
            HttpHeaders headers = request.getHeaders();
            log.debug("Headers:");
            headers.forEach((name, values) -> {
                log.debug("  {}: {}", name, String.join(", ", values));
            });

            String userAgent = headers.getFirst("User-Agent");
            String dockerClient = headers.getFirst("Docker-Client");
            if (userAgent != null) {
                log.debug("User-Agent: {}", userAgent);
            }
            if (dockerClient != null) {
                log.debug("Docker-Client: {}", dockerClient);
            }
        }

        log.debug("=======================");

        return chain.filter(exchange)
                .doOnSuccess(v -> {
                    if (log.isDebugEnabled()) {
                        log.debug("Response Status: {}", exchange.getResponse().getStatusCode());
                    }
                })
                .doOnError(error -> {
                    log.error("Request failed: {}", error.getMessage());
                });
    }
}
