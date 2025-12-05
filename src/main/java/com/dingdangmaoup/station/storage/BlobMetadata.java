package com.dingdangmaoup.station.storage;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class BlobMetadata {
    private String digest;
    private long size;
    private Instant createdAt;
    private Instant lastAccessedAt;
    private String contentType;
}
