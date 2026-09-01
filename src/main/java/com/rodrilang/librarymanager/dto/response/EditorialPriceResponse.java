package com.rodrilang.librarymanager.dto.response;

import com.rodrilang.librarymanager.editorialprice.enums.EditorialPriceAuthority;
import com.rodrilang.librarymanager.editorialprice.enums.EffectiveEditorialPriceDeterminationType;

import java.math.BigDecimal;
import java.time.LocalDate;

public record EditorialPriceResponse(
        BigDecimal price,
        LocalDate validFrom,
        EffectiveEditorialPriceDeterminationType determinationType,
        EditorialPriceAuthority authority
) {

    public static EditorialPriceResponse empty() {
        return new EditorialPriceResponse(
                null,
                null,
                null,
                null
        );
    }
}