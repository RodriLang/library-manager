package com.rodrilang.librarymanager.importer.price.dto.internal;

import com.rodrilang.librarymanager.enums.BookSource;

import java.math.BigDecimal;

public record PriceListRow(

        int rowNumber,

        String isbn,

        String title,

        String authorName,

        String publisherName,

        BigDecimal retailPrice,

        String categoryName,

        BookSource sourceName,

        PriceListMetadata metadata

) {

    public String preferredIdentifier() {
        if (metadata != null
                && metadata.externalCode() != null
                && !metadata.externalCode().isBlank()) {
            return metadata.externalCode();
        }

        return isbn;
    }
}