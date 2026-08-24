package com.rodrilang.librarymanager.purchasing.requirement.dto.response;

public record BookPurchaseRequirementStatusResponse(
        boolean pending,
        Long requirementId,
        Integer quantity,
        Long preferredProviderId,
        String preferredProviderName
) {

    public static BookPurchaseRequirementStatusResponse notPending() {
        return new BookPurchaseRequirementStatusResponse(
                false,
                null,
                null,
                null,
                null
        );
    }
}