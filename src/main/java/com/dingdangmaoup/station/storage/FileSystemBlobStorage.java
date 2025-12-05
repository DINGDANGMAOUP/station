package com.dingdangmaoup.station.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.file.*;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
public class FileSystemBlobStorage implements BlobStorage {

    private final String basePath;
    private final String tempDir;
    private final int chunkSize;
    private final DefaultDataBufferFactory bufferFactory;

    public FileSystemBlobStorage(
            @Value("${station.storage.base-path:/data/station}") String basePath,
            @Value("${station.storage.temp-dir:${station.storage.base-path}/temp}") String tempDir,
            @Value("${station.storage.blob-chunk-size:65536}") int chunkSize) {
        this.basePath = basePath;
        this.tempDir = tempDir;
        this.chunkSize = chunkSize;
        this.bufferFactory = new DefaultDataBufferFactory();
    }

    @Override
    public Mono<BlobMetadata> saveBlob(String digest, Flux<DataBuffer> data) {
        return Mono.defer(() -> {
            try {
                Path tempFile = getTempPath(UUID.randomUUID().toString());
                Path finalPath = getBlobPath(digest);

                Files.createDirectories(finalPath.getParent());

                log.debug("Saving blob {} to temporary file: {}", digest, tempFile);

                return DataBufferUtils.write(data, tempFile, StandardOpenOption.CREATE_NEW)
                        .then(Mono.defer(() -> {
                            try {
                                Files.move(tempFile, finalPath, StandardCopyOption.ATOMIC_MOVE,
                                        StandardCopyOption.REPLACE_EXISTING);

                                long size = Files.size(finalPath);
                                log.info("Successfully saved blob {} ({} bytes)", digest, size);

                                return Mono.just(BlobMetadata.builder()
                                        .digest(digest)
                                        .size(size)
                                        .createdAt(Instant.now())
                                        .lastAccessedAt(Instant.now())
                                        .build());
                            } catch (IOException e) {
                                log.error("Failed to move blob {} to final location", digest, e);
                                return Mono.error(new StorageException("Failed to save blob", e));
                            }
                        }))
                        .onErrorResume(error -> {
                            // Clean up temp file on error
                            try {
                                Files.deleteIfExists(tempFile);
                            } catch (IOException e) {
                                log.warn("Failed to cleanup temp file: {}", tempFile, e);
                            }
                            return Mono.error(new StorageException("Failed to save blob", error));
                        });
            } catch (Exception e) {
                return Mono.error(new StorageException("Failed to initialize blob save", e));
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<DataBuffer> getBlob(String digest) {
        return Mono.fromCallable(() -> getBlobPath(digest))
                .flatMapMany(path -> {
                    if (!Files.exists(path)) {
                        return Flux.error(new StorageException("Blob not found: " + digest));
                    }

                    log.debug("Reading blob {} from: {}", digest, path);

                    return DataBufferUtils.read(path, bufferFactory, chunkSize)
                            .doOnComplete(() -> log.debug("Completed reading blob {}", digest))
                            .doOnError(error -> log.error("Error reading blob {}", digest, error));
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<DataBuffer> getBlob(String digest, long offset, long length) {
        return Mono.fromCallable(() -> getBlobPath(digest))
                .flatMapMany(path -> {
                    if (!Files.exists(path)) {
                        return Flux.error(new StorageException("Blob not found: " + digest));
                    }

                    log.debug("Reading blob {} range: offset={}, length={}", digest, offset, length);

                    try {
                        AsynchronousFileChannel channel = AsynchronousFileChannel.open(path,
                                StandardOpenOption.READ);

                        return DataBufferUtils.readAsynchronousFileChannel(
                                        () -> channel, offset, bufferFactory, chunkSize)
                                .take((long) Math.ceil((double) length / chunkSize))
                                .doFinally(signalType -> {
                                    try {
                                        channel.close();
                                    } catch (IOException e) {
                                        log.warn("Failed to close file channel", e);
                                    }
                                });
                    } catch (IOException e) {
                        return Flux.error(new StorageException("Failed to open blob file", e));
                    }
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Boolean> exists(String digest) {
        return Mono.fromCallable(() -> Files.exists(getBlobPath(digest)))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<BlobMetadata> getMetadata(String digest) {
        return Mono.fromCallable(() -> {
            Path path = getBlobPath(digest);
            if (!Files.exists(path)) {
                throw new StorageException("Blob not found: " + digest);
            }

            return BlobMetadata.builder()
                    .digest(digest)
                    .size(Files.size(path))
                    .createdAt(Instant.ofEpochMilli(Files.getLastModifiedTime(path).toMillis()))
                    .lastAccessedAt(Instant.now())
                    .build();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Boolean> delete(String digest) {
        return Mono.fromCallable(() -> {
            Path path = getBlobPath(digest);
            boolean deleted = Files.deleteIfExists(path);
            if (deleted) {
                log.info("Deleted blob: {}", digest);
            }
            return deleted;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Long> getTotalSize() {
        return Mono.fromCallable(() -> {
            Path blobsPath = Paths.get(basePath, "blobs");
            if (!Files.exists(blobsPath)) {
                return 0L;
            }

            return Files.walk(blobsPath)
                    .filter(Files::isRegularFile)
                    .mapToLong(path -> {
                        try {
                            return Files.size(path);
                        } catch (IOException e) {
                            log.warn("Failed to get size of file: {}", path, e);
                            return 0L;
                        }
                    })
                    .sum();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Long> getAvailableSpace() {
        return Mono.fromCallable(() -> {
            Path path = Paths.get(basePath);
            return Files.getFileStore(path).getUsableSpace();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private Path getBlobPath(String digest) {
        // Parse digest format: "sha256:abc123..." -> algorithm="sha256", hash="abc123..."
        int colonIndex = digest.indexOf(':');
        if (colonIndex == -1) {
            throw new IllegalArgumentException("Invalid digest format (missing ':'): " + digest);
        }

        String algorithm = digest.substring(0, colonIndex);     // "sha256"
        String hash = digest.substring(colonIndex + 1);         // "abc123def456..."

        if (hash.length() < 2) {
            throw new IllegalArgumentException("Invalid digest format (hash too short): " + digest);
        }

        String prefix = hash.substring(0, 2);

        return Paths.get(basePath, "blobs", algorithm, prefix, hash);
    }

    private Path getTempPath(String tempId) {
        return Paths.get(tempDir, "downloads", tempId);
    }
}
