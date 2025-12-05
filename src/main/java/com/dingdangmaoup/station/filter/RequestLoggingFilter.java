package com.dingdangmaoup.station.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Request logging filter to debug Docker client requests
 */
@Slf4j
@Component
@Order(-1) // Execute before all other filters
public class RequestLoggingFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // Log request details
        log.info("=== Incoming Request ===");
        log.info("Method: {}", request.getMethod());
        log.info("Path: {}", request.getPath().value());
        log.info("URI: {}", request.getURI());
        log.info("Query: {}", request.getURI().getQuery());

        // Log headers
        HttpHeaders headers = request.getHeaders();
        log.info("Headers:");
        headers.forEach((name, values) -> {
            log.info("  {}: {}", name, String.join(", ", values));
        });

        // Log Docker-specific headers
        String userAgent = headers.getFirst("User-Agent");
        String dockerClient = headers.getFirst("Docker-Client");
        if (userAgent != null) {
            log.info("User-Agent: {}", userAgent);
        }
        if (dockerClient != null) {
            log.info("Docker-Client: {}", dockerClient);
        }

        log.info("=======================");

        return chain.filter(exchange)
                .doOnSuccess(v -> {
                    log.info("Response Status: {}", exchange.getResponse().getStatusCode());
                })
                .doOnError(error -> {
                    log.error("Request failed: {}", error.getMessage());
                });
    }
}
