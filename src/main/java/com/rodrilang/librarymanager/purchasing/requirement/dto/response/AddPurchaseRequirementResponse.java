package com.rodrilang.librarymanager.purchasing.requirement.dto.response;

import com.rodrilang.librarymanager.purchasing.requirement.model.PurchaseRequirementSourceType;

import java.util.List;

public record AddPurchaseRequirementResponse(

        Long requirementId,

        Long bookId,
        String isbn,
        String title,
        String coverUrl,

        Integer previousQuantity,
        Integer addedQuantity,
        Integer currentQuantity,

        Long sourceId,
        PurchaseRequirementSourceType source,

        Long preferredProviderId,
        String preferredProviderName,

        List<PurchaseRequirementReasonResponse> reasons

) {
}