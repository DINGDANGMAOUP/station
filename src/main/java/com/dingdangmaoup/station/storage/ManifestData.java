package com.dingdangmaoup.station.storage;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Manifest data with content and metadata
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManifestData {
    private String fullName;
    private String reference;
    private String digest;
    private String contentType;
    private String content;
    private Long size;
}
