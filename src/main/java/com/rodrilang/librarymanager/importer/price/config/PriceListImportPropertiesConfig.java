package com.rodrilang.librarymanager.importer.price.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PriceListImportProperties.class)
public class PriceListImportPropertiesConfig {
}