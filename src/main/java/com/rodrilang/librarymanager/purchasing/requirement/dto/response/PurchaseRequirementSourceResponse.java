package com.rodrilang.librarymanager.purchasing.requirement.dto.response;

import com.rodrilang.librarymanager.purchasing.requirement.model.PurchaseRequirementSourceType;

import java.time.Instant;

public record PurchaseRequirementSourceResponse(

        Long id,
        PurchaseRequirementSourceType type,
        Integer quantity,

        String referenceId,

        Long providerId,
        String providerName,

        Instant createdAt

) {
}