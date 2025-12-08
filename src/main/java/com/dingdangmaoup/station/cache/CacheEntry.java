package com.dingdangmaoup.station.cache;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CacheEntry implements Serializable {
    private String digest;
    private Long size;
    private String nodeId;
    private String contentType;
    private Instant timestamp;
    private Object data;

    public static CacheEntry forManifest(String digest, long size, String nodeId, String contentType) {
        return CacheEntry.builder()
                .digest(digest)
                .size(size)
                .nodeId(nodeId)
                .contentType(contentType)
                .timestamp(Instant.now())
                .build();
    }

    public static CacheEntry forBlob(String digest, long size, String nodeId) {
        return CacheEntry.builder()
                .digest(digest)
                .size(size)
                .nodeId(nodeId)
                .timestamp(Instant.now())
                .build();
    }
}
