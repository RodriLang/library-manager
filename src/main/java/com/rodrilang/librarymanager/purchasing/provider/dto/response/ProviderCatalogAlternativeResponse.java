package com.rodrilang.librarymanager.purchasing.provider.dto.response;

import java.math.BigDecimal;

public record ProviderCatalogAlternativeResponse(

        Long providerId,
        String providerName,
        BigDecimal price

) {
}