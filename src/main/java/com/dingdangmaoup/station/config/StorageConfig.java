package com.dingdangmaoup.station.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Configuration
public class StorageConfig {

    @Value("${station.storage.base-path:/data/station}")
    private String basePath;

    @Value("${station.storage.temp-dir:${station.storage.base-path}/temp}")
    private String tempDir;

    @PostConstruct
    public void initializeStorage() {
        try {
            Path base = Paths.get(basePath);
            Path blobs = base.resolve("blobs/sha256");
            Path manifests = base.resolve("manifests");
            Path temp = Paths.get(tempDir);
            Path downloads = temp.resolve("downloads");

            Files.createDirectories(blobs);
            Files.createDirectories(manifests);
            Files.createDirectories(downloads);

            log.info("Initialized storage directories:");
            log.info("  Base path: {}", base.toAbsolutePath());
            log.info("  Blobs: {}", blobs.toAbsolutePath());
            log.info("  Manifests: {}", manifests.toAbsolutePath());
            log.info("  Temp: {}", temp.toAbsolutePath());

            if (!Files.isWritable(base)) {
                throw new IllegalStateException("Base storage path is not writable: " + base);
            }

        } catch (IOException e) {
            log.error("Failed to initialize storage directories", e);
            throw new RuntimeException("Failed to initialize storage", e);
        }
    }

    public String getBasePath() {
        return basePath;
    }

    public String getTempDir() {
        return tempDir;
    }
}
