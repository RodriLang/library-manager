package com.rodrilang.librarymanager.purchasing.requirement.dto.request;

import jakarta.validation.constraints.NotNull;

public record AdjustPurchaseRequirementRequest(

        @NotNull
        Integer quantity

) {
}