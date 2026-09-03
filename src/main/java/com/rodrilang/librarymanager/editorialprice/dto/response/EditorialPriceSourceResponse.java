package com.rodrilang.librarymanager.editorialprice.dto.response;

import com.rodrilang.librarymanager.editorialprice.enums.EditorialPriceOrigin;
import com.rodrilang.librarymanager.editorialprice.enums.ExternalPriceSourceType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record EditorialPriceSourceResponse(
        Long id,
        BigDecimal price,
        String currency,
        LocalDate validFrom,
        EditorialPriceOrigin origin,
        Long providerId,
        String providerName,
        String providerCode,
        ExternalPriceSourceType externalSourceType,
        String sourceName,
        String sourceUrl,
        String sourceNote,
        String createdByUsername,
        LocalDate lastConfirmedOn,
        List<EditorialPriceConfirmationResponse> confirmations
) {
}