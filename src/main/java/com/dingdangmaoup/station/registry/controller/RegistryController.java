package com.dingdangmaoup.station.registry.controller;

import com.dingdangmaoup.station.cache.CacheEntry;
import com.dingdangmaoup.station.cache.CacheKey;
import com.dingdangmaoup.station.cache.MultiLevelCacheManager;
import com.dingdangmaoup.station.cache.PeerCacheService;
import com.dingdangmaoup.station.coordination.DistributedLock;
import com.dingdangmaoup.station.docker.DockerHubClient;
import com.dingdangmaoup.station.metrics.NodeMetrics;
import com.dingdangmaoup.station.storage.BlobStorage;
import com.dingdangmaoup.station.storage.ManifestStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Docker Registry API v2
 */
@Slf4j
@RestController
@RequestMapping("/v2")
@RequiredArgsConstructor
public class RegistryController {

    private final BlobStorage blobStorage;
    private final ManifestStorage manifestStorage;
    private final MultiLevelCacheManager cacheManager;
    private final PeerCacheService peerCacheService;
    private final DockerHubClient dockerHubClient;
    private final DistributedLock distributedLock;
    private final NodeMetrics nodeMetrics;

    @Value("${station.node.id}")
    private String nodeId;

    /**
     * API version check endpoint
     */
    @GetMapping("/")
    public Mono<ResponseEntity<Void>> apiVersion() {
      log.info("apiVersion");
        return Mono.just(ResponseEntity.ok()
                .header("Docker-Distribution-API-Version", "registry/2.0")
                .build());
    }

    /**
     * Get manifest - supports both simple (alpine) and namespaced (library/alpine) image names
     */
    @GetMapping(value = {
            "/{name}/manifests/{reference}",
            "/{namespace}/{name}/manifests/{reference}"
    })
    public Mono<ResponseEntity<String>> getManifest(
            @PathVariable(required = false) String namespace,
            @PathVariable String name,
            @PathVariable String reference) {

        String fullName = (namespace != null) ? namespace + "/" + name : name;
        log.info("GET manifest: {}:{}", fullName, reference);

        CacheKey key = CacheKey.forManifest(fullName, reference);

        return cacheManager.get(key)
                .flatMap(optEntry -> {
                    if (optEntry.isPresent()) {
                        CacheEntry entry = optEntry.get();
                        log.info("Cache HIT for manifest: {}:{}", fullName, reference);

                        String content = entry.getData() != null ? entry.getData().toString() : "";
                        return Mono.just(ResponseEntity.ok()
                                .header("Docker-Content-Digest", entry.getDigest())
                                .contentType(MediaType.parseMediaType(entry.getContentType()))
                                .body(content));
                    }

                    // L1 & L2 miss - try storage (L2.5: file system)
                    log.info("Cache MISS at L1/L2 for manifest: {}:{}, checking storage", fullName, reference);
                    return manifestStorage.getManifest(fullName, reference)
                            .flatMap(storageResult -> {
                                if (storageResult.isPresent()) {
                                    // Found in storage - restore to cache
                                    var manifestData = storageResult.get();
                                    log.info("Storage HIT for manifest: {}:{}", fullName, reference);

                                    CacheEntry newEntry = CacheEntry.builder()
                                            .digest(manifestData.getDigest())
                                            .size(manifestData.getSize())
                                            .contentType(manifestData.getContentType())
                                            .nodeId(nodeId)
                                            .data(manifestData.getContent())
                                            .build();

                                    return cacheManager.put(key, newEntry)
                                            .thenReturn(ResponseEntity.ok()
                                                    .header("Docker-Content-Digest", manifestData.getDigest())
                                                    .contentType(MediaType.parseMediaType(manifestData.getContentType()))
                                                    .body(manifestData.getContent()));
                                }

                                // Storage miss - try L3 (peer nodes) with consistent hashing
                                log.info("Storage MISS for manifest: {}:{}, checking peers (L3)", fullName, reference);
                    return peerCacheService.queryPeersForManifest(fullName, reference)
                            .flatMap(peerResult -> {
                                if (peerResult.isPresent()) {
                                    var manifestData = peerResult.get();
                                    log.info("Found manifest {}:{} in peer cache", fullName, reference);

                                    String content = manifestData.getChunk().toStringUtf8();

                                    CacheEntry newEntry = CacheEntry.builder()
                                            .digest(manifestData.getDigest())
                                            .size((long) content.length())
                                            .contentType(manifestData.getContentType())
                                            .nodeId(nodeId)
                                            .data(content)
                                            .build();

                                    return Mono.when(
                                            cacheManager.put(key, newEntry),
                                            manifestStorage.saveManifest(fullName, reference, content,
                                                    manifestData.getDigest(), manifestData.getContentType())
                                    ).thenReturn(ResponseEntity.ok()
                                            .header("Docker-Content-Digest", manifestData.getDigest())
                                            .contentType(MediaType.parseMediaType(manifestData.getContentType()))
                                            .body(content));
                                }

                                // L3 miss - fetch from Docker Hub (L4)
                                log.info("Cache MISS at all peer nodes for manifest: {}:{}, fetching from Docker Hub (L4)",
                                        fullName, reference);

                                String lockKey = "manifest:" + fullName + ":" + reference;
                                return distributedLock.withLock(lockKey,
                                        dockerHubClient.getManifest(fullName, reference)
                                                .flatMap(manifestResponse -> {
                                                    nodeMetrics.recordManifestDownload();

                                                    CacheEntry newEntry = CacheEntry.builder()
                                                            .digest(manifestResponse.getDigest())
                                                            .size(manifestResponse.getSize())
                                                            .contentType(manifestResponse.getContentType())
                                                            .nodeId(nodeId)
                                                            .data(manifestResponse.getContent())
                                                            .build();

                                                    return Mono.when(
                                                            cacheManager.put(key, newEntry),
                                                            manifestStorage.saveManifest(fullName, reference,
                                                                    manifestResponse.getContent(),
                                                                    manifestResponse.getDigest(),
                                                                    manifestResponse.getContentType())
                                                    ).thenReturn(ResponseEntity.ok()
                                                            .header("Docker-Content-Digest", manifestResponse.getDigest())
                                                            .contentType(MediaType.parseMediaType(manifestResponse.getContentType()))
                                                            .body(manifestResponse.getContent()));
                                                })
                                );
                            });
                            });
                })
                .onErrorResume(error -> {
                    log.error("Error fetching manifest {}:{}", fullName, reference, error);
                    return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
                });
    }

    /**
     * HEAD manifest (check if exists)
     */
    @RequestMapping(value = {
            "/{name}/manifests/{reference}",
            "/{namespace}/{name}/manifests/{reference}"
    }, method = RequestMethod.HEAD)
    public Mono<ResponseEntity<Void>> headManifest(
            @PathVariable(required = false) String namespace,
            @PathVariable String name,
            @PathVariable String reference) {

        String fullName = (namespace != null) ? namespace + "/" + name : name;
        CacheKey key = CacheKey.forManifest(fullName, reference);

        return cacheManager.get(key)
                .map(optEntry -> {
                    if (optEntry.isPresent()) {
                        CacheEntry entry = optEntry.get();
                        return ResponseEntity.ok()
                                .header("Docker-Content-Digest", entry.getDigest())
                                .contentType(MediaType.parseMediaType(entry.getContentType()))
                                .contentLength(entry.getSize())
                                .build();
                    }
                    return ResponseEntity.<Void>status(HttpStatus.NOT_FOUND).build();
                });
    }

    /**
     * GET blob
     */
    @GetMapping(value = {
            "/{name}/blobs/{digest}",
            "/{namespace}/{name}/blobs/{digest}"
    })
    public Mono<Void> getBlob(
            @PathVariable(required = false) String namespace,
            @PathVariable String name,
            @PathVariable String digest,
            ServerHttpResponse response) {

        String fullName = (namespace != null) ? namespace + "/" + name : name;
        log.info("GET blob: {} for {}", digest, fullName);

        return blobStorage.exists(digest)
                .flatMap(exists -> {
                    if (exists) {
                        // Blob exists locally (L1 - storage)
                        log.info("Blob HIT: {}", digest);
                        return blobStorage.getMetadata(digest)
                                .flatMap(metadata -> {
                                    response.getHeaders().setContentType(MediaType.APPLICATION_OCTET_STREAM);
                                    response.getHeaders().setContentLength(metadata.getSize());
                                    response.getHeaders().set("Docker-Content-Digest", digest);

                                    Flux<DataBuffer> dataFlux = blobStorage.getBlob(digest);
                                    return response.writeWith(dataFlux);
                                });
                    }

                    // Blob miss locally - try L3 (peer nodes) with consistent hashing
                    log.info("Blob MISS locally: {}, checking peers (L3)", digest);
                    return peerCacheService.queryPeersForBlob(digest)
                            .flatMap(peerResult -> {
                                if (peerResult.isPresent()) {
                                    log.info("Found blob {} in peer cache", digest);
                                    Flux<DataBuffer> peerDataFlux = peerResult.get();

                                    return blobStorage.saveBlob(digest, peerDataFlux)
                                            .flatMap(metadata -> {
                                                CacheKey key = CacheKey.forBlob(digest);
                                                CacheEntry entry = CacheEntry.forBlob(digest, metadata.getSize(), nodeId);

                                                return cacheManager.put(key, entry)
                                                        .then(blobStorage.getMetadata(digest));
                                            })
                                            .flatMap(metadata -> {
                                                response.getHeaders().setContentType(MediaType.APPLICATION_OCTET_STREAM);
                                                response.getHeaders().setContentLength(metadata.getSize());
                                                response.getHeaders().set("Docker-Content-Digest", digest);

                                                return response.writeWith(blobStorage.getBlob(digest));
                                            });
                                }

                                // L3 miss - fetch from Docker Hub (L4)
                                log.info("Blob MISS at all peer nodes: {}, fetching from Docker Hub (L4)", digest);

                                String lockKey = "blob:" + digest;
                                return distributedLock.withLock(lockKey,
                                        blobStorage.saveBlob(digest, dockerHubClient.streamBlob(fullName, digest))
                                                .flatMap(metadata -> {
                                                    nodeMetrics.recordBlobDownload(metadata.getSize());

                                                    CacheKey key = CacheKey.forBlob(digest);
                                                    CacheEntry entry = CacheEntry.forBlob(digest, metadata.getSize(), nodeId);

                                                    return cacheManager.put(key, entry)
                                                            .then(blobStorage.getMetadata(digest));
                                                })
                                                .flatMap(metadata -> {
                                                    response.getHeaders().setContentType(MediaType.APPLICATION_OCTET_STREAM);
                                                    response.getHeaders().setContentLength(metadata.getSize());
                                                    response.getHeaders().set("Docker-Content-Digest", digest);

                                                    return response.writeWith(blobStorage.getBlob(digest));
                                                })
                                );
                            });
                })
                .onErrorResume(error -> {
                    log.error("Error fetching blob: {}", digest, error);
                    response.setStatusCode(HttpStatus.NOT_FOUND);
                    return response.setComplete();
                });
    }

    /**
     * HEAD blob (check if exists)
     */
    @RequestMapping(value = {
            "/{name}/blobs/{digest}",
            "/{namespace}/{name}/blobs/{digest}"
    }, method = RequestMethod.HEAD)
    public Mono<ResponseEntity<Void>> headBlob(
            @PathVariable String digest) {

        return blobStorage.exists(digest)
                .flatMap(exists -> {
                    if (exists) {
                        return blobStorage.getMetadata(digest)
                                .map(metadata -> ResponseEntity.ok()
                                        .header("Docker-Content-Digest", digest)
                                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                                        .contentLength(metadata.getSize())
                                        .build());
                    }
                    return Mono.just(ResponseEntity.<Void>status(HttpStatus.NOT_FOUND).build());
                });
    }
}
