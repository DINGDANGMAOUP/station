package com.dingdangmaoup.station.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Node metrics collection
 */
@Slf4j
@Component
public class NodeMetrics {

    private final Counter blobDownloadCounter;
    private final Counter manifestDownloadCounter;
    private final AtomicLong totalBlobBytes = new AtomicLong(0);
    private final AtomicLong activeNodeCount = new AtomicLong(0);

    public NodeMetrics(MeterRegistry meterRegistry) {
        this.blobDownloadCounter = Counter.builder("station.blob.download")
                .description("Total number of blob downloads")
                .register(meterRegistry);

        this.manifestDownloadCounter = Counter.builder("station.manifest.download")
                .description("Total number of manifest downloads")
                .register(meterRegistry);

        Gauge.builder("station.blob.total_bytes", totalBlobBytes, AtomicLong::get)
                .description("Total bytes of cached blobs")
                .register(meterRegistry);

        Gauge.builder("station.node.active_count", activeNodeCount, AtomicLong::get)
                .description("Number of active nodes in cluster")
                .register(meterRegistry);
    }

    public void recordBlobDownload(long bytes) {
        blobDownloadCounter.increment();
        totalBlobBytes.addAndGet(bytes);
    }

    public void recordManifestDownload() {
        manifestDownloadCounter.increment();
    }

    public void setActiveNodeCount(long count) {
        activeNodeCount.set(count);
    }

    public long getTotalBlobBytes() {
        return totalBlobBytes.get();
    }
}
