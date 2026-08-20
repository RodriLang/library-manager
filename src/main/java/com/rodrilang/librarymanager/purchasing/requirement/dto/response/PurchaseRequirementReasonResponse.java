package com.rodrilang.librarymanager.purchasing.requirement.dto.response;

import com.rodrilang.librarymanager.purchasing.requirement.model.PurchaseRequirementSourceType;

public record PurchaseRequirementReasonResponse(

        PurchaseRequirementSourceType type,
        Integer quantity

) {
}