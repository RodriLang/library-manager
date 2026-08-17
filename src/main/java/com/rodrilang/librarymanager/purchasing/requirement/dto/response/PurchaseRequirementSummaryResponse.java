package com.rodrilang.librarymanager.purchasing.requirement.dto.response;

import com.rodrilang.librarymanager.purchasing.requirement.model.PurchaseRequirementStatus;

import java.util.List;

public record PurchaseRequirementSummaryResponse(

        Long id,

        Long bookId,
        String isbn,
        String title,
        String coverUrl,

        Integer quantity,

        PurchaseRequirementInventoryResponse inventory,

        Long preferredProviderId,
        String preferredProviderName,

        List<PurchaseRequirementReasonResponse> reasons,

        PurchaseRequirementStatus status

) {
}