package com.rodrilang.librarymanager.importer.price.dto;

import com.rodrilang.librarymanager.enums.BookSource;
import com.rodrilang.librarymanager.importer.price.parser.PriceListSource;

import java.math.BigDecimal;

public record PriceListRow(

        int rowNumber,

        String isbn,

        String title,

        String authorName,

        String publisherName,

        BigDecimal retailPrice,

        PriceListSource priceListSource,

        String categoryName,

        BookSource sourceName,

        PriceListMetadata metadata

) {

    /*
     * Mantiene compatibles los parsers actuales.
     */
    public PriceListRow(
            int rowNumber,
            String isbn,
            String title,
            String authorName,
            String publisherName,
            BigDecimal retailPrice,
            PriceListSource source,
            String categoryName,
            BookSource bookSource
    ) {
        this(
                rowNumber,
                isbn,
                title,
                authorName,
                publisherName,
                retailPrice,
                source,
                categoryName,
                bookSource,
                null
        );
    }

    public String preferredIdentifier() {
        if (metadata != null
                && metadata.externalCode() != null
                && !metadata.externalCode().isBlank()) {
            return metadata.externalCode();
        }

        return isbn;
    }
}