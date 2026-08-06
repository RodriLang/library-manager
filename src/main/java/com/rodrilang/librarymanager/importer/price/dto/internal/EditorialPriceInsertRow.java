package com.rodrilang.librarymanager.importer.price.dto.internal;

import java.math.BigDecimal;

public record EditorialPriceInsertRow(
        Long bookId,
        BigDecimal price
) {
}