package com.dingdangmaoup.station.storage;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Metadata for a stored manifest
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManifestMetadata {
    private String fullName;
    private String reference;
    private String digest;
    private String contentType;
    private Long size;
    private Instant createdAt;
    private Instant lastAccessedAt;
}
