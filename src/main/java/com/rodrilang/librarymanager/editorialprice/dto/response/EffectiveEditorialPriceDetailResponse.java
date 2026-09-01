package com.rodrilang.librarymanager.editorialprice.dto.response;

import com.rodrilang.librarymanager.editorialprice.enums.EditorialPriceAuthority;
import com.rodrilang.librarymanager.editorialprice.enums.EffectiveEditorialPriceDeterminationType;

import java.math.BigDecimal;
import java.time.LocalDate;

public record EffectiveEditorialPriceDetailResponse(
        Long id,
        BigDecimal price,
        String currency,
        LocalDate validFrom,
        EffectiveEditorialPriceDeterminationType determinationType,
        EditorialPriceAuthority authority
) {
}