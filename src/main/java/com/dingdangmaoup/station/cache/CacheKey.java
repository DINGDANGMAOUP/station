package com.dingdangmaoup.station.cache;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CacheKey implements Serializable {
    private String type;  // "manifest" or "blob"
    private String repository;
    private String reference;  // tag or digest
    private String digest;

    public static CacheKey forManifest(String repository, String reference) {
        return CacheKey.builder()
                .type("manifest")
                .repository(repository)
                .reference(reference)
                .build();
    }

    public static CacheKey forBlob(String digest) {
        return CacheKey.builder()
                .type("blob")
                .digest(digest)
                .build();
    }

    public String toRedisKey() {
        if ("manifest".equals(type)) {
            return String.format("cache:manifest:%s:%s", repository, reference);
        } else {
            return String.format("cache:blob:%s", digest);
        }
    }

    @Override
    public String toString() {
        return toRedisKey();
    }
}
