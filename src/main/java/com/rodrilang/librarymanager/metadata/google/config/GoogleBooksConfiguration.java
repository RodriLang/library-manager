package com.rodrilang.librarymanager.metadata.google.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(GoogleBooksProperties.class)
public class GoogleBooksConfiguration {
}
