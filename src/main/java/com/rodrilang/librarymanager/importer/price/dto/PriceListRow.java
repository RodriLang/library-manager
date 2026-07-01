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

        BookSource sourceName

) {
}