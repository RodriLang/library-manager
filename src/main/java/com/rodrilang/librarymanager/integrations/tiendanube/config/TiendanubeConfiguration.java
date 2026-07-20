package com.rodrilang.librarymanager.integrations.tiendanube.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(TiendanubeProperties.class)
public class TiendanubeConfiguration {

    @Bean
    public RestClient tiendanubeRestClient(RestClient.Builder builder) {
        return builder.build();
    }

}