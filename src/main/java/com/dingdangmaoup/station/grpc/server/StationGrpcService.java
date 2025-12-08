package com.dingdangmaoup.station.grpc.server;

import com.dingdangmaoup.station.cache.CacheEntry;
import com.dingdangmaoup.station.cache.CacheKey;
import com.dingdangmaoup.station.cache.MultiLevelCacheManager;
import com.dingdangmaoup.station.grpc.*;
import com.dingdangmaoup.station.storage.BlobStorage;
import com.google.protobuf.ByteString;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * gRPC service implementation for inter-node communication
 */
@Slf4j
@GrpcService
@RequiredArgsConstructor
public class StationGrpcService extends ReactorStationServiceGrpc.StationServiceImplBase {

    private final BlobStorage blobStorage;
    private final MultiLevelCacheManager cacheManager;

    @Value("${station.node.id}")
    private String nodeId;

    @Override
    public Mono<ManifestResponse> hasManifest(Mono<ManifestRequest> request) {
        return request.flatMap(req -> {
            CacheKey key = CacheKey.forManifest(req.getRepository(), req.getReference());

            return cacheManager.get(key)
                    .map(optEntry -> {
                        if (optEntry.isPresent()) {
                            CacheEntry entry = optEntry.get();
                            log.debug("HasManifest HIT: {}", key);
                            return ManifestResponse.newBuilder()
                                    .setExists(true)
                                    .setDigest(entry.getDigest())
                                    .setSize(entry.getSize())
                                    .setContentType(entry.getContentType() != null ? entry.getContentType() : "")
                                    .build();
                        } else {
                            log.debug("HasManifest MISS: {}", key);
                            return ManifestResponse.newBuilder()
                                    .setExists(false)
                                    .build();
                        }
                    });
        });
    }

    @Override
    public Flux<ManifestData> getManifest(Mono<ManifestRequest> request) {
        return request.flatMapMany(req -> {
            CacheKey key = CacheKey.forManifest(req.getRepository(), req.getReference());

            return cacheManager.get(key)
                    .flatMapMany(optEntry -> {
                        if (optEntry.isEmpty()) {
                            return Flux.error(Status.NOT_FOUND
                                    .withDescription("Manifest not found")
                                    .asException());
                        }

                        CacheEntry entry = optEntry.get();

                        if (entry.getData() instanceof String manifestData) {
                            return Flux.just(ManifestData.newBuilder()
                                    .setChunk(ByteString.copyFromUtf8(manifestData))
                                    .setContentType(entry.getContentType())
                                    .setDigest(entry.getDigest())
                                    .build());
                        }

                        return Flux.error(Status.INTERNAL
                                .withDescription("Manifest data not available")
                                .asException());
                    });
        });
    }

    @Override
    public Mono<BlobResponse> hasBlob(Mono<BlobRequest> request) {
        return request.flatMap(req -> {
            String digest = req.getDigest();

            return blobStorage.exists(digest)
                    .flatMap(exists -> {
                        if (exists) {
                            log.debug("HasBlob HIT: {}", digest);
                            return blobStorage.getMetadata(digest)
                                    .map(metadata -> BlobResponse.newBuilder()
                                            .setExists(true)
                                            .setSize(metadata.getSize())
                                            .build());
                        } else {
                            log.debug("HasBlob MISS: {}", digest);
                            return Mono.just(BlobResponse.newBuilder()
                                    .setExists(false)
                                    .build());
                        }
                    });
        });
    }

    @Override
    public Flux<BlobData> getBlob(Mono<BlobRequest> request) {
        return request.flatMapMany(req -> {
            String digest = req.getDigest();
            long offset = req.getOffset();
            long length = req.getLength();

            Flux<DataBuffer> dataFlux;
            if (offset > 0 || length > 0) {
                dataFlux = blobStorage.getBlob(digest, offset, length);
            } else {
                dataFlux = blobStorage.getBlob(digest);
            }

            return dataFlux
                    .map(dataBuffer -> {
                        byte[] bytes = new byte[dataBuffer.readableByteCount()];
                        dataBuffer.read(bytes);
                        DataBufferUtils.release(dataBuffer);

                        return BlobData.newBuilder()
                                .setChunk(ByteString.copyFrom(bytes))
                                .setOffset(offset)
                                .build();
                    })
                    .doOnError(error -> log.error("Error streaming blob: {}", digest, error));
        });
    }

    @Override
    public Mono<HealthCheckResponse> healthCheck(Mono<HealthCheckRequest> request) {
        return request.map(req -> {
            // Simple health check - can be enhanced with actual metrics
            return HealthCheckResponse.newBuilder()
                    .setStatus(HealthCheckResponse.Status.HEALTHY)
                    .setCacheUsagePercent(0)
                    .setRequestsPerSecond(0)
                    .setTotalCachedBlobs(0)
                    .setTotalCachedBytes(0)
                    .build();
        });
    }

    @Override
    public Mono<NodeInfoResponse> getNodeInfo(Mono<NodeInfoRequest> request) {
        return request.flatMap(req ->
                blobStorage.getTotalSize()
                        .zipWith(blobStorage.getAvailableSpace())
                        .map(tuple -> NodeInfoResponse.newBuilder()
                                .setNodeId(nodeId)
                                .setHost("localhost")
                                .setGrpcPort(50051)
                                .setHttpPort(5000)
                                .setUptimeSeconds(0)
                                .setTotalCachedBytes(tuple.getT1())
                                .setAvailableStorageBytes(tuple.getT2())
                                .build())
        );
    }
}
