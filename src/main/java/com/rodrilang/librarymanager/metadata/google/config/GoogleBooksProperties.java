package com.rodrilang.librarymanager.metadata.google.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "google-books")
public record GoogleBooksProperties(
        String apiKey
) {

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
