package com.rodrilang.librarymanager.importer.price.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.price-import")
public record PriceListImportProperties(

        @Min(10) int batchSize,
        @Min(10) int stagingBatchSize,
        @NotNull DataSize maxFileSize,
        @Min(1) int maxConcurrentImports
) {
}