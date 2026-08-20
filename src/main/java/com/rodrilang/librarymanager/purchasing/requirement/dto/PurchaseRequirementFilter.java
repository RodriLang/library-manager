package com.rodrilang.librarymanager.purchasing.requirement.dto;

import com.rodrilang.librarymanager.purchasing.requirement.model.PurchaseRequirementStatus;

public record PurchaseRequirementFilter(

        String query,
        Long providerId,
        PurchaseRequirementStatus status

) {
}