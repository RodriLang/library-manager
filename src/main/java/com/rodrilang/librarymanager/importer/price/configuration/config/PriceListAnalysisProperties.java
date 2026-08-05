package com.rodrilang.librarymanager.importer.price.configuration.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.price-list.analysis")
public record PriceListAnalysisProperties(

        @Min(1) int maxPreviewRows,
        @Min(1) int maxColumns,
        @Min(1) int maxSheets,
        @NotNull DataSize maxFileSize
) {
}