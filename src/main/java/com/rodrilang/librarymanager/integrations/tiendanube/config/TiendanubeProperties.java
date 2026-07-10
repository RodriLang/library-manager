package com.rodrilang.librarymanager.integrations.tiendanube.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tiendanube")
public record TiendanubeProperties(

        String clientId,
        String clientSecret,
        String redirectUri,
        String authUrl,
        String tokenUrl,
        String apiUrl

) {
}