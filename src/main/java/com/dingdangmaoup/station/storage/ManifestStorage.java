package com.dingdangmaoup.station.storage;

import reactor.core.publisher.Mono;

import java.util.Optional;

/**
 * Storage interface for Docker manifest files
 */
public interface ManifestStorage {

    /**
     * Save a manifest to storage
     *
     * @param fullName    the image name (e.g., library/nginx)
     * @param reference   the reference (tag like "latest" OR digest like "sha256:abc123...")
     * @param content     the manifest JSON content
     * @param digest      the manifest digest (SHA256)
     * @param contentType the content type
     * @return Mono emitting the saved manifest metadata
     */
    Mono<ManifestMetadata> saveManifest(String fullName, String reference, String content,
                                        String digest, String contentType);

    /**
     * Get a manifest from storage
     *
     * @param fullName  the image name
     * @param reference the reference (tag like "latest" OR digest like "sha256:abc123...")
     * @return Mono emitting Optional of manifest data
     */
    Mono<Optional<ManifestData>> getManifest(String fullName, String reference);

    /**
     * Check if a manifest exists
     *
     * @param fullName  the image name
     * @param reference the reference
     * @return Mono emitting true if exists
     */
    Mono<Boolean> exists(String fullName, String reference);

    /**
     * Get manifest metadata
     *
     * @param fullName  the image name
     * @param reference the reference
     * @return Mono emitting manifest metadata
     */
    Mono<ManifestMetadata> getMetadata(String fullName, String reference);

    /**
     * Delete a manifest
     *
     * @param fullName  the image name
     * @param reference the reference
     * @return Mono emitting true if deleted
     */
    Mono<Boolean> delete(String fullName, String reference);

    /**
     * Get total number of manifests
     *
     * @return Mono emitting count
     */
    Mono<Long> getCount();

    /**
     * Get total storage size used by manifests
     *
     * @return Mono emitting size in bytes
     */
    Mono<Long> getTotalSize();
}
