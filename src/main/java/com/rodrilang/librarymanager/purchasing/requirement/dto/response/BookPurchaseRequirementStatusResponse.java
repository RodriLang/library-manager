package com.rodrilang.librarymanager.purchasing.requirement.dto.response;

public record BookPurchaseRequirementStatusResponse(
        boolean pending,
        Long requirementId,
        Integer quantity,
        Integer orderedQuantity,
        Integer remainingQuantity,
        Long preferredProviderId,
        String preferredProviderName
) {

    public static BookPurchaseRequirementStatusResponse notPending() {
        return notPending(null, null);
    }

    public static BookPurchaseRequirementStatusResponse notPending(
            Long preferredProviderId,
            String preferredProviderName
    ) {
        return new BookPurchaseRequirementStatusResponse(
                false,
                null,
                0,
                0,
                0,
                preferredProviderId,
                preferredProviderName
        );
    }
}
