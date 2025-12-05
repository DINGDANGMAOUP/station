package com.dingdangmaoup.station.docker;

import com.dingdangmaoup.station.config.properties.DockerProperties;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.net.URI;
import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class DockerHubClient {

    private final WebClient dockerHubWebClient;
    private final DockerAuthService authService;
    private final DockerProperties dockerProperties;

    private static final String MANIFEST_V2_MEDIA_TYPE = "application/vnd.docker.distribution.manifest.v2+json";
    private static final String MANIFEST_LIST_MEDIA_TYPE = "application/vnd.docker.distribution.manifest.list.v2+json";
    private static final String OCI_INDEX_MEDIA_TYPE = "application/vnd.oci.image.index.v1+json";

    /**
     * Get manifest from Docker Hub
     */
    public Mono<ManifestResponse> getManifest(String repository, String reference) {
        return authService.getAuthToken(repository)
                .flatMap(token -> dockerHubWebClient.get()
                        .uri("/v2/{name}/manifests/{reference}", repository, reference)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .accept(
                                MediaType.parseMediaType(MANIFEST_V2_MEDIA_TYPE),
                                MediaType.parseMediaType(MANIFEST_LIST_MEDIA_TYPE),
                                MediaType.parseMediaType(OCI_INDEX_MEDIA_TYPE)
                        )
                        .retrieve()
                        .toEntity(String.class)
                        .map(response -> {
                            String digest = response.getHeaders().getFirst("Docker-Content-Digest");
                            String contentType = response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
                            String body = response.getBody();

                            log.debug("Fetched manifest for {}: digest={}, size={}",
                                    repository + ":" + reference, digest, body != null ? body.length() : 0);

                            return ManifestResponse.builder()
                                    .digest(digest)
                                    .contentType(contentType)
                                    .content(body)
                                    .size(body != null ? body.length() : 0)
                                    .build();
                        }))
                .retryWhen(Retry.backoff(
                        dockerProperties.getRetry().getMaxAttempts(),
                        dockerProperties.getRetry().getBackoffDelay())
                        .filter(throwable -> !(throwable instanceof WebClientResponseException.NotFound)))
                .onErrorMap(WebClientResponseException.class, ex ->
                        new DockerHubException("Failed to fetch manifest: " + ex.getMessage(),
                                ex.getStatusCode().value(), ex));
    }

    /**
     * Check if manifest exists
     */
    public Mono<Boolean> manifestExists(String repository, String reference) {
        return authService.getAuthToken(repository)
                .flatMap(token -> dockerHubWebClient.head()
                        .uri("/v2/{name}/manifests/{reference}", repository, reference)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .retrieve()
                        .toBodilessEntity()
                        .map(response -> true)
                        .onErrorReturn(WebClientResponseException.NotFound.class, false));
    }

    /**
     * Stream blob from Docker Hub
     */
    public Flux<DataBuffer> streamBlob(String repository, String digest) {
        return authService.getAuthToken(repository)
                .flatMapMany(token -> dockerHubWebClient.get()
                        .uri("/v2/{name}/blobs/{digest}", repository, digest)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .exchangeToFlux(response -> {
                            log.debug("Response status for blob {}: {}", digest, response.statusCode());

                            if (response.statusCode().is3xxRedirection()) {
                                String location = response.headers().asHttpHeaders().getFirst(HttpHeaders.LOCATION);
                                log.debug("Blob {} redirected to: {}", digest, location);

                                if (location != null) {
                                    try {
                                        URI cdnUri = URI.create(location);
                                        return WebClient.create()
                                                .get()
                                                .uri(cdnUri)
                                                .retrieve()
                                                .bodyToFlux(DataBuffer.class)
                                                .doOnSubscribe(s -> log.debug("Streaming blob {} from CDN: {}", digest, location))
                                                .doOnComplete(() -> log.debug("Completed streaming blob {} from CDN", digest))
                                                .doOnError(err -> log.error("Error streaming blob {} from CDN", digest, err));
                                    } catch (Exception e) {
                                        log.error("Failed to parse redirect URI: {}", location, e);
                                        return Flux.error(e);
                                    }
                                }
                            }

                            // No redirect, stream directly
                            return response.bodyToFlux(DataBuffer.class)
                                    .doOnSubscribe(s -> log.debug("Streaming blob {} directly", digest))
                                    .doOnNext(buffer -> log.debug("Received buffer of {} bytes for blob {}",
                                            buffer.readableByteCount(), digest))
                                    .doOnComplete(() -> log.debug("Completed streaming blob {}", digest))
                                    .doOnError(err -> log.error("Error streaming blob {}", digest, err));
                        }))
                .retryWhen(Retry.backoff(
                        dockerProperties.getRetry().getMaxAttempts(),
                        dockerProperties.getRetry().getBackoffDelay())
                        .filter(throwable -> !(throwable instanceof WebClientResponseException.NotFound)))
                .onErrorMap(WebClientResponseException.class, ex ->
                        new DockerHubException("Failed to stream blob: " + ex.getMessage(),
                                ex.getStatusCode().value(), ex));
    }

    /**
     * Check if blob exists
     */
    public Mono<Boolean> blobExists(String repository, String digest) {
        return authService.getAuthToken(repository)
                .flatMap(token -> dockerHubWebClient.head()
                        .uri("/v2/{name}/blobs/{digest}", repository, digest)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .retrieve()
                        .toBodilessEntity()
                        .map(response -> true)
                        .onErrorReturn(WebClientResponseException.NotFound.class, false));
    }

    @Data
    @Builder
    public static class ManifestResponse {
        private String digest;
        private String contentType;
        private String content;
        private long size;
    }
}
