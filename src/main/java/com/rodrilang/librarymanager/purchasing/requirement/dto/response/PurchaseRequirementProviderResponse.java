package com.rodrilang.librarymanager.purchasing.requirement.dto.response;

import java.math.BigDecimal;

public record PurchaseRequirementProviderResponse(

        Long providerId,
        String providerName,
        BigDecimal price

) {
}