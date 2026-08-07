package com.rodrilang.librarymanager.importer.price.dto.internal;

import java.math.BigDecimal;

public record PriceListResolvedPrice(

        Long stagingId,

        Long bookId,

        int selectedRowNumber,

        String selectedIsbn,

        BigDecimal selectedPrice,

        int firstRowNumber,

        String firstIsbn,

        BigDecimal firstPrice,

        BigDecimal minPrice,

        BigDecimal maxPrice,

        int occurrenceCount,

        int conflictingPriceCount

) {

    public boolean duplicated() {
        return occurrenceCount > 1;
    }

    public boolean hasConflictingPrices() {
        return minPrice.compareTo(maxPrice) != 0;
    }
}