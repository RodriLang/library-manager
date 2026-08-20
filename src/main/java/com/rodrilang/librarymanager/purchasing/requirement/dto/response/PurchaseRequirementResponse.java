package com.rodrilang.librarymanager.purchasing.requirement.dto.response;

import com.rodrilang.librarymanager.purchasing.requirement.model.PurchaseRequirementStatus;

public record PurchaseRequirementResponse(

        Long id,

        Long bookId,
        String isbn,
        String title,
        String coverUrl,

        Integer quantity,

        Long preferredProviderId,
        String preferredProviderName,

        PurchaseRequirementStatus status

) {
}