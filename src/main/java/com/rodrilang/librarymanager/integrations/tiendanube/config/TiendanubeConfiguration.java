package com.rodrilang.librarymanager.integrations.tiendanube.config;

import com.rodrilang.librarymanager.integrations.tiendanube.job.config.TiendanubeJobProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties({TiendanubeProperties.class, TiendanubeJobProperties.class})
public class TiendanubeConfiguration {

    @Bean
    public RestClient tiendanubeRestClient(RestClient.Builder builder, TiendanubeProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Math.toIntExact(properties.connectTimeout().toMillis()));
        requestFactory.setReadTimeout(Math.toIntExact(properties.readTimeout().toMillis()));

        return builder
                .baseUrl(properties.apiUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
