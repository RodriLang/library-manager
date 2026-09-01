package com.rodrilang.librarymanager.importer.price.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record EffectiveEditorialPriceResponse(
        Long bookId,
        BigDecimal price,
        String currency,
        LocalDate validFrom
) {
}