package com.rodrilang.librarymanager.editorialprice.dto.request;

import com.rodrilang.librarymanager.editorialprice.enums.ExternalPriceSourceType;

public record EditorialPriceMetadataUpdateRequest(
        ExternalPriceSourceType externalSourceType,
        String sourceName,
        String sourceUrl,
        String note
) {
}