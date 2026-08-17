package com.rodrilang.librarymanager.purchasing.requirement.dto.request;

import com.rodrilang.librarymanager.purchasing.requirement.model.PurchaseRequirementSourceType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AddPurchaseRequirementRequest(

        @NotNull
        Long bookId,

        @NotNull
        @Positive
        Integer quantity,

        @NotNull
        PurchaseRequirementSourceType source,

        Long providerId

) {
}