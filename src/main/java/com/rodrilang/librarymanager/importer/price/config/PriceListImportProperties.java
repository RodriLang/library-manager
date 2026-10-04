package com.rodrilang.librarymanager.importer.price.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.price-import")
public record PriceListImportProperties(
        @NotNull DataSize maxFileSize
) {
}
