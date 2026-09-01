package com.rodrilang.librarymanager.editorialprice.dto.request;

import com.rodrilang.librarymanager.editorialprice.enums.EditorialPriceConfirmationSourceType;
import com.rodrilang.librarymanager.editorialprice.enums.ExternalPriceSourceType;

import java.time.LocalDate;

public record EditorialPriceConfirmationRequest(

        LocalDate confirmedOn,

        EditorialPriceConfirmationSourceType sourceType,

        Long providerId,

        ExternalPriceSourceType externalSourceType,

        String sourceName,

        String sourceUrl,

        String note
) {
}