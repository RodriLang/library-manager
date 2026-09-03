package com.rodrilang.librarymanager.editorialprice.dto.request;

import com.rodrilang.librarymanager.editorialprice.enums.EditorialPriceOrigin;
import com.rodrilang.librarymanager.editorialprice.enums.ExternalPriceSourceType;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ManualEditorialPriceRequest(

        BigDecimal price,

        LocalDate validFrom,

        LocalDate confirmedOn,

        EditorialPriceOrigin origin,

        Long providerId,

        ExternalPriceSourceType externalSourceType,

        String sourceName,

        String sourceUrl,

        String note
) {
}