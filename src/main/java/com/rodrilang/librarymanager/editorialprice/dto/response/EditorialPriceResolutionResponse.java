package com.rodrilang.librarymanager.editorialprice.dto.response;

import com.rodrilang.librarymanager.editorialprice.enums.EditorialPriceResolutionType;

import java.math.BigDecimal;
import java.time.LocalDate;

public record EditorialPriceResolutionResponse(
        Long id,
        LocalDate validFrom,
        Long selectedEditorialPriceId,
        BigDecimal resolvedPrice,
        EditorialPriceResolutionType resolutionType,
        String note,
        String resolvedByUsername
) {
}