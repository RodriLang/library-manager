package com.rodrilang.librarymanager.media.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cloudinary")
public record CloudinaryProperties(
        String cloudName,
        String apiKey,
        String apiSecret,
        String rootFolder
) {

    public CloudinaryProperties {
        if (rootFolder == null || rootFolder.isBlank()) {
            rootFolder = "anaquel";
        }
    }
}