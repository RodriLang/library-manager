package com.rodrilang.librarymanager.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient openLibraryRestClient() {
        return RestClient.builder()
                .baseUrl("https://openlibrary.org")
                .defaultHeader(HttpHeaders.USER_AGENT, "library-manager/1.0")
                .build();
    }

    @Bean
    public RestClient googleBooksRestClient() {
        return RestClient.builder()
                .baseUrl("https://www.googleapis.com")
                .defaultHeader(HttpHeaders.USER_AGENT, "library-manager/1.0")
                .build();
    }

    @Bean
    public RestClient coverProviderRestClient() {
        return RestClient.builder()
                .build();
    }
}