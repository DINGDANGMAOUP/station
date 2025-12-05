package com.dingdangmaoup.station.docker;

import com.dingdangmaoup.station.docker.model.DockerAuthToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class DockerAuthService {

    @Value("${station.docker.auth-url:https://auth.docker.io}")
    private String authUrl;

    private final ConcurrentHashMap<String, DockerAuthToken> tokenCache = new ConcurrentHashMap<>();

    /**
     * Get authentication token for Docker Hub
     */
    public Mono<String> getAuthToken(String repository) {
        String cacheKey = "docker.io:" + repository;

        DockerAuthToken cachedToken = tokenCache.get(cacheKey);
        if (cachedToken != null) {
            log.debug("Using cached Docker Hub token for: {}", repository);
            return Mono.just(cachedToken.getEffectiveToken());
        }

        return fetchToken(repository)
                .doOnNext(token -> {
                    tokenCache.put(cacheKey, token);
                    log.info("Fetched new Docker Hub token for: {}", repository);
                })
                .map(DockerAuthToken::getEffectiveToken);
    }

    private Mono<DockerAuthToken> fetchToken(String repository) {
        String service = "registry.docker.io";
        String scope = String.format("repository:%s:pull", repository);

        WebClient authClient = WebClient.create(authUrl);

        return authClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/token")
                        .queryParam("service", service)
                        .queryParam("scope", scope)
                        .build())
                .retrieve()
                .bodyToMono(DockerAuthToken.class)
                .timeout(Duration.ofSeconds(10))
                .doOnError(error -> log.error("Failed to fetch Docker Hub token for: {}", repository, error));
    }

    /**
     * Clear token cache
     */
    public void clearCache() {
        tokenCache.clear();
        log.info("Cleared Docker Hub token cache");
    }
}
