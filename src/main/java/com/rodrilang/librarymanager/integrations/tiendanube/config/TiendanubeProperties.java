package com.rodrilang.librarymanager.integrations.tiendanube.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "tiendanube")
public record TiendanubeProperties(
        String clientId,
        String clientSecret,
        String redirectUri,
        String authUrl,
        String tokenUrl,
        String apiUrl,
        Duration connectTimeout,
        Duration readTimeout,
        Endpoints endpoints
) {

    public TiendanubeProperties {
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(5) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(20) : readTimeout;
    }

    public record Endpoints(
            String products,
            String product,
            String productVariant,
            String productImages,
            String productImage,
            String productsPage,
            String orders,
            String webhooks
    ) {
    }
}
