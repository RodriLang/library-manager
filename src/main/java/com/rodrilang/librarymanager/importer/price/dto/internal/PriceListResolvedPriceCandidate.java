package com.rodrilang.librarymanager.importer.price.dto.internal;

import java.math.BigDecimal;

public record PriceListResolvedPriceCandidate(

        Long bookId,

        int rowNumber,

        String isbn,

        BigDecimal price

) {
}