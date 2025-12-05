package com.dingdangmaoup.station.config;

import com.dingdangmaoup.station.config.properties.CacheProperties;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@EnableCaching
@RequiredArgsConstructor
public class CacheConfig {

    private final CacheProperties cacheProperties;

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(cacheProperties.getLocal().getMaxEntries())
                .expireAfterWrite(cacheProperties.getLocal().getTtl())
                .recordStats());

        log.info("Initialized Caffeine cache manager with maxEntries={}, ttl={}, maxSize={}",
                cacheProperties.getLocal().getMaxEntries(),
                cacheProperties.getLocal().getTtl(),
                cacheProperties.getLocal().getMaxSize());
        return cacheManager;
    }

    @Bean
    public com.github.benmanes.caffeine.cache.Cache<String, Object> localCache() {
        long maxWeightBytes = cacheProperties.getLocal().getMaxSize().toBytes();

        return Caffeine.newBuilder()
                .maximumWeight(maxWeightBytes)
                .weigher((String key, Object value) -> {
                    int keySize = key.length() * 2;
                    int valueSize = estimateObjectSize(value);
                    return keySize + valueSize;
                })
                .expireAfterWrite(cacheProperties.getLocal().getTtl())
                .recordStats()
                .build();
    }

    /**
     * Estimate object size in bytes
     */
    private int estimateObjectSize(Object obj) {
        if (obj == null) return 0;
        if (obj instanceof String) {
            return ((String) obj).length() * 2;
        }
        if (obj instanceof byte[]) {
            return ((byte[]) obj).length;
        }
        return 1024;
    }
}
