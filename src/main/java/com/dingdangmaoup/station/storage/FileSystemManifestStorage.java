package com.dingdangmaoup.station.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

/**
 * File-system-based implementation of ManifestStorage
 * Stores manifests in the directory structure:
 *
 */
@Slf4j
@Component
public class FileSystemManifestStorage implements ManifestStorage {

    private final String basePath;

    public FileSystemManifestStorage(
            @Value("${station.storage.base-path:/data/station}") String basePath) {
        this.basePath = basePath;
        initializeDirectories();
    }

    private void initializeDirectories() {
        try {
            Path manifestsPath = Paths.get(basePath, "manifests");
            Files.createDirectories(manifestsPath);
            log.info("Initialized manifest storage at: {}", manifestsPath);
        } catch (IOException e) {
            log.error("Failed to initialize manifest storage directories", e);
            throw new StorageException("Failed to initialize manifest storage", e);
        }
    }

    @Override
    public Mono<ManifestMetadata> saveManifest(String fullName, String reference, String content,
                                               String digest, String contentType) {
        return Mono.fromCallable(() -> {
            Path manifestPath = getManifestPath(fullName, reference);

            Files.createDirectories(manifestPath.getParent());

            byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
            Files.write(manifestPath, contentBytes,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);

            long size = contentBytes.length;
            log.info("Saved manifest {}:{} to {} ({} bytes)", fullName, reference, manifestPath, size);

            return ManifestMetadata.builder()
                    .fullName(fullName)
                    .reference(reference)
                    .digest(digest)
                    .contentType(contentType)
                    .size(size)
                    .createdAt(Instant.now())
                    .lastAccessedAt(Instant.now())
                    .build();
        }).subscribeOn(Schedulers.boundedElastic())
                .onErrorMap(e -> new StorageException("Failed to save manifest", e));
    }

    @Override
    public Mono<Optional<ManifestData>> getManifest(String fullName, String reference) {
        return Mono.fromCallable(() -> {
            Path manifestPath = getManifestPath(fullName, reference);

            if (!Files.exists(manifestPath)) {
                log.debug("Manifest not found: {}:{}", fullName, reference);
                return Optional.<ManifestData>empty();
            }

            byte[] contentBytes = Files.readAllBytes(manifestPath);
            String content = new String(contentBytes, StandardCharsets.UTF_8);

            String digest = calculateDigest(contentBytes);

            log.debug("Read manifest {}:{} from {} ({} bytes)",
                    fullName, reference, manifestPath, contentBytes.length);

            return Optional.of(ManifestData.builder()
                    .fullName(fullName)
                    .reference(reference)
                    .content(content)
                    .digest(digest)
                    .size((long) contentBytes.length)
                    .build());
        }).subscribeOn(Schedulers.boundedElastic())
                .onErrorMap(e -> new StorageException("Failed to read manifest", e));
    }

    @Override
    public Mono<Boolean> exists(String fullName, String reference) {
        return Mono.fromCallable(() ->
                Files.exists(getManifestPath(fullName, reference))
        ).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<ManifestMetadata> getMetadata(String fullName, String reference) {
        return Mono.fromCallable(() -> {
            Path manifestPath = getManifestPath(fullName, reference);

            if (!Files.exists(manifestPath)) {
                throw new StorageException("Manifest not found: " + fullName + ":" + reference);
            }

            long size = Files.size(manifestPath);
            Instant lastModified = Instant.ofEpochMilli(
                    Files.getLastModifiedTime(manifestPath).toMillis());

            return ManifestMetadata.builder()
                    .fullName(fullName)
                    .reference(reference)
                    .size(size)
                    .createdAt(lastModified)
                    .lastAccessedAt(Instant.now())
                    .build();
        }).subscribeOn(Schedulers.boundedElastic())
                .onErrorMap(e -> new StorageException("Failed to get manifest metadata", e));
    }

    @Override
    public Mono<Boolean> delete(String fullName, String reference) {
        return Mono.fromCallable(() -> {
            Path manifestPath = getManifestPath(fullName, reference);
            boolean deleted = Files.deleteIfExists(manifestPath);

            if (deleted) {
                log.info("Deleted manifest: {}:{}", fullName, reference);
            }

            return deleted;
        }).subscribeOn(Schedulers.boundedElastic())
                .onErrorMap(e -> new StorageException("Failed to delete manifest", e));
    }

    @Override
    public Mono<Long> getCount() {
        return Mono.fromCallable(() -> {
            Path manifestsPath = Paths.get(basePath, "manifests");
            if (!Files.exists(manifestsPath)) {
                return 0L;
            }

            return Files.walk(manifestsPath)
                    .filter(path -> path.toString().endsWith(".json"))
                    .count();
        }).subscribeOn(Schedulers.boundedElastic())
                .onErrorReturn(0L);
    }

    @Override
    public Mono<Long> getTotalSize() {
        return Mono.fromCallable(() -> {
            Path manifestsPath = Paths.get(basePath, "manifests");
            if (!Files.exists(manifestsPath)) {
                return 0L;
            }

            return Files.walk(manifestsPath)
                    .filter(path -> path.toString().endsWith(".json"))
                    .mapToLong(path -> {
                        try {
                            return Files.size(path);
                        } catch (IOException e) {
                            log.warn("Failed to get size of file: {}", path, e);
                            return 0L;
                        }
                    })
                    .sum();
        }).subscribeOn(Schedulers.boundedElastic())
                .onErrorReturn(0L);
    }

    /**
     * Get the file path for a manifest
     * Format: /basePath/manifests/{namespace}/{name}/{reference}.json
     * Example: /data/station/manifests/library/nginx/latest.json
     * Example: /data/station/manifests/library/nginx/sha256_abc123.json (when reference is a digest)
     */
    private Path getManifestPath(String fullName, String reference) {
        String safeReference = sanitizeFilename(reference);
        return Paths.get(basePath, "manifests", fullName, safeReference + ".json");
    }

    /**
     * Sanitize filename to remove potentially dangerous characters
     */
    private String sanitizeFilename(String filename) {
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    /**
     * Calculate SHA256 digest of content
     */
    private String calculateDigest(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            return "sha256:" + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new StorageException("Failed to calculate digest", e);
        }
    }
}
