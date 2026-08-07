package com.rodrilang.librarymanager.cover.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "anaquel.cover-processing")
public record BookCoverProcessingProperties(
        Boolean enabled,
        Integer batchSize,
        Integer parallelism,
        Integer maxAttempts,
        Duration fixedDelay,
        Duration processingTimeout
) {

    public BookCoverProcessingProperties {
        if (enabled == null) {
            enabled = true;
        }

        if (batchSize == null || batchSize <= 0) {
            batchSize = 5;
        }

        if (parallelism == null || parallelism <= 0) {
            parallelism = 3;
        }

        if (maxAttempts == null || maxAttempts <= 0) {
            maxAttempts = 4;
        }

        if (fixedDelay == null || fixedDelay.isNegative()) {
            fixedDelay = Duration.ofSeconds(10);
        }

        if (
                processingTimeout == null
                        || processingTimeout.isZero()
                        || processingTimeout.isNegative()
        ) {
            processingTimeout = Duration.ofMinutes(15);
        }
    }
}