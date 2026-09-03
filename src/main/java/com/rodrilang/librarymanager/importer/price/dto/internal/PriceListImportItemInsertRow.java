package com.rodrilang.librarymanager.importer.price.dto.internal;

import com.rodrilang.librarymanager.importer.price.enums.EditorialPriceChange;
import com.rodrilang.librarymanager.importer.price.enums.PriceListImportItemOperation;

import java.math.BigDecimal;

public record PriceListImportItemInsertRow(
        Long bookId,
        Long editorialPriceId,
        BigDecimal importedPrice,
        BigDecimal previousPrice,
        PriceListImportItemOperation operation,
        EditorialPriceChange priceChange
) {
}