package com.dingdangmaoup.station.storage;

import org.springframework.core.io.buffer.DataBuffer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface BlobStorage {

    /**
     * Save a blob to storage
     *
     * @param digest the blob digest (SHA256)
     * @param data   the blob data stream
     * @return Mono emitting the saved blob metadata
     */
    Mono<BlobMetadata> saveBlob(String digest, Flux<DataBuffer> data);

    /**
     * Get a blob from storage
     *
     * @param digest the blob digest
     * @return Flux emitting the blob data
     */
    Flux<DataBuffer> getBlob(String digest);

    /**
     * Get a blob with range support
     *
     * @param digest the blob digest
     * @param offset starting byte offset
     * @param length number of bytes to read
     * @return Flux emitting the blob data chunk
     */
    Flux<DataBuffer> getBlob(String digest, long offset, long length);

    /**
     * Check if a blob exists
     *
     * @param digest the blob digest
     * @return Mono emitting true if exists
     */
    Mono<Boolean> exists(String digest);

    /**
     * Get blob metadata
     *
     * @param digest the blob digest
     * @return Mono emitting blob metadata
     */
    Mono<BlobMetadata> getMetadata(String digest);

    /**
     * Delete a blob
     *
     * @param digest the blob digest
     * @return Mono emitting true if deleted
     */
    Mono<Boolean> delete(String digest);

    /**
     * Get total storage size used
     *
     * @return Mono emitting size in bytes
     */
    Mono<Long> getTotalSize();

    /**
     * Get available storage space
     *
     * @return Mono emitting available bytes
     */
    Mono<Long> getAvailableSpace();
}
