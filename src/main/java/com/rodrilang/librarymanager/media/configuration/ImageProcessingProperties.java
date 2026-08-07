package com.rodrilang.librarymanager.media.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;

@ConfigurationProperties(prefix = "anaquel.media")
public record ImageProcessingProperties(
        DataSize maxFileSize,
        DataSize maxDownloadSize,
        Set<String> allowedContentTypes,
        Integer minimumWidth,
        Integer minimumHeight,
        Duration connectTimeout,
        Duration readTimeout
) {

    public ImageProcessingProperties {
        if (maxFileSize == null) {
            maxFileSize = DataSize.ofMegabytes(5);
        }

        if (maxDownloadSize == null) {
            maxDownloadSize = DataSize.ofMegabytes(10);
        }

        if (allowedContentTypes == null || allowedContentTypes.isEmpty()) {
            allowedContentTypes = Set.of(
                    "image/jpeg",
                    "image/png",
                    "image/webp"
            );
        }

        if (minimumWidth == null || minimumWidth <= 0) {
            minimumWidth = 200;
        }

        if (minimumHeight == null || minimumHeight <= 0) {
            minimumHeight = 300;
        }

        if (
                connectTimeout == null
                        || connectTimeout.isNegative()
                        || connectTimeout.isZero()
        ) {
            connectTimeout = Duration.ofSeconds(10);
        }

        if (
                readTimeout == null
                        || readTimeout.isNegative()
                        || readTimeout.isZero()
        ) {
            readTimeout = Duration.ofSeconds(20);
        }

        allowedContentTypes = allowedContentTypes.stream()
                .map(String::trim)
                .map(String::toLowerCase)
                .collect(Collectors.toUnmodifiableSet());
    }
}