package com.rodrilang.librarymanager.importer.price.dto.internal;

public record PriceListStagingRow(
        Long id,
        Integer rowNumber,
        PriceListRow row
) {
}