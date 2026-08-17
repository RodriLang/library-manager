package com.rodrilang.librarymanager.purchasing.requirement.dto.internal;

import com.rodrilang.librarymanager.purchasing.requirement.model.PurchaseRequirementSourceType;

public record AddPurchaseRequirementCommand(

        Long bookId,
        Integer quantity,
        PurchaseRequirementSourceType source,
        String referenceId,
        Long providerId

) {
}