package com.rodrilang.librarymanager.editorialprice.dto.response;

import com.rodrilang.librarymanager.editorialprice.enums.EditorialPriceConfirmationSourceType;
import com.rodrilang.librarymanager.editorialprice.enums.ExternalPriceSourceType;

import java.time.LocalDate;

public record EditorialPriceConfirmationResponse(
        Long id,
        LocalDate confirmedOn,
        EditorialPriceConfirmationSourceType sourceType,
        Long providerId,
        String providerName,
        ExternalPriceSourceType externalSourceType,
        String sourceName,
        String sourceUrl,
        String note,
        String createdByUsername
) {
}