package com.rodrilang.librarymanager.importer.price.dto.response;

import com.rodrilang.librarymanager.importer.price.enums.EditorialPriceChange;
import com.rodrilang.librarymanager.importer.price.enums.PriceListImportItemOperation;

import java.math.BigDecimal;
import java.util.List;

public record PriceListImportItemResponse(

        Long id,

        Long bookId,

        String isbn,

        String title,

        List<String> authors,

        String publisher,

        Long editorialPriceId,

        BigDecimal previousPrice,

        BigDecimal importedPrice,

        BigDecimal changePercentage,

        PriceListImportItemOperation operation,

        EditorialPriceChange priceChange

) {
}