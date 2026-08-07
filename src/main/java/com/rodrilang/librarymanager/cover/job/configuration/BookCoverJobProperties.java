package com.rodrilang.librarymanager.cover.job.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "anaquel.cover-jobs")
public record BookCoverJobProperties(
        Boolean enabled,
        Integer batchSize,
        Integer maxAttempts,
        Duration pollDelay,
        Duration processingTimeout
) {

    public BookCoverJobProperties {
        if (enabled == null) {
            enabled = true;
        }

        if (batchSize == null || batchSize <= 0) {
            batchSize = 5;
        }

        if (maxAttempts == null || maxAttempts <= 0) {
            maxAttempts = 4;
        }

        if (pollDelay == null || pollDelay.isNegative()) {
            pollDelay = Duration.ofSeconds(5);
        }

        if (
                processingTimeout == null
                        || processingTimeout.isNegative()
                        || processingTimeout.isZero()
        ) {
            processingTimeout = Duration.ofMinutes(15);
        }
    }
}