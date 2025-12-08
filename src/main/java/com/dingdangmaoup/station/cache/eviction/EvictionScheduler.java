package com.dingdangmaoup.station.cache.eviction;

import com.dingdangmaoup.station.storage.BlobStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Scheduled cache eviction based on storage thresholds
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "station.eviction.enabled", havingValue = "true", matchIfMissing = true)
public class EvictionScheduler {

    private final BlobStorage blobStorage;

    @Value("${station.storage.base-path:/data/station}")
    private String basePath;

    @Value("${station.storage.temp-dir:${station.storage.base-path}/temp}")
    private String tempDir;

    @Value("${station.eviction.threshold:90}")
    private int thresholdPercent;

    /**
     * Check storage usage and evict if necessary (runs every hour)
     */
    @Scheduled(fixedDelayString = "${station.eviction.check-interval:3600000}") // 1 hour
    public void checkAndEvict() {
        log.debug("Running cache eviction check");

        blobStorage.getAvailableSpace()
                .zipWith(blobStorage.getTotalSize())
                .flatMap(tuple -> {
                    long availableSpace = tuple.getT1();
                    long usedSpace = tuple.getT2();
                    long totalSpace = availableSpace + usedSpace;

                    double usagePercent = (double) usedSpace / totalSpace * 100;

                    log.info("Storage usage: {}% ({} / {} bytes), available: {} bytes",
                            usagePercent, usedSpace, totalSpace, availableSpace);

                    if (usagePercent > thresholdPercent) {
                        log.warn("Storage usage ({}%) exceeds threshold ({}%), starting eviction",
                                usagePercent, thresholdPercent);
                        return evictOldestBlobs(usedSpace - (totalSpace * thresholdPercent / 100));
                    }

                    return Mono.empty();
                })
                .doOnError(error -> log.error("Error during eviction check", error))
                .subscribe();
    }

    /**
     * Clean up orphaned files in temp directory (runs every hour)
     */
    @Scheduled(fixedDelayString = "${station.eviction.check-interval:3600000}")
    public void cleanupOrphans() {
        log.debug("Running orphan cleanup");

        Path tempDownloadsDir = Paths.get(tempDir, "downloads");

        if (!Files.exists(tempDownloadsDir)) {
            return;
        }

        try (Stream<Path> files = Files.list(tempDownloadsDir)) {
            List<Path> orphans = files
                    .filter(Files::isRegularFile)
                    .filter(this::isOlderThanOneHour)
                    .toList();

            if (!orphans.isEmpty()) {
                log.info("Found {} orphaned temp files, cleaning up", orphans.size());

                orphans.forEach(path -> {
                    try {
                        Files.delete(path);
                        log.debug("Deleted orphaned temp file: {}", path);
                    } catch (IOException e) {
                        log.warn("Failed to delete orphaned temp file: {}", path, e);
                    }
                });
            }
        } catch (IOException e) {
            log.error("Error during orphan cleanup", e);
        }
    }

    private Mono<Void> evictOldestBlobs(long bytesToFree) {
        return Mono.fromRunnable(() -> {
            Path blobsPath = Paths.get(basePath, "blobs", "sha256");

            if (!Files.exists(blobsPath)) {
                return;
            }

            try (Stream<Path> files = Files.walk(blobsPath)) {
                List<Path> sortedFiles = files
                        .filter(Files::isRegularFile)
                        .sorted(Comparator.comparing(this::getLastAccessTime))
                        .toList();

                long freedBytes = 0;
                int evictedCount = 0;

                for (Path file : sortedFiles) {
                    if (freedBytes >= bytesToFree) {
                        break;
                    }

                    try {
                        long fileSize = Files.size(file);
                        Files.delete(file);
                        freedBytes += fileSize;
                        evictedCount++;
                        log.debug("Evicted blob: {} ({} bytes)", file.getFileName(), fileSize);
                    } catch (IOException e) {
                        log.warn("Failed to evict blob: {}", file, e);
                    }
                }

                log.info("Eviction completed: freed {} bytes by evicting {} blobs",
                        freedBytes, evictedCount);

            } catch (IOException e) {
                log.error("Error during blob eviction", e);
            }
        });
    }

    private boolean isOlderThanOneHour(Path path) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
            Instant fileTime = attrs.lastModifiedTime().toInstant();
            return fileTime.isBefore(Instant.now().minus(Duration.ofHours(1)));
        } catch (IOException e) {
            log.warn("Failed to read file attributes: {}", path, e);
            return false;
        }
    }

    private Instant getLastAccessTime(Path path) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
            return attrs.lastAccessTime().toInstant();
        } catch (IOException e) {
            log.warn("Failed to read file attributes: {}", path, e);
            return Instant.MIN;
        }
    }
}
