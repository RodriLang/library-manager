package com.rodrilang.librarymanager.importer.price.dto.internal;

import java.math.BigDecimal;

public record EditorialPriceUpdateRow(
        Long editorialPriceId,
        BigDecimal price
) {
}