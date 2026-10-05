package com.rodrilang.librarymanager.purchasing.requirement.dto.response;

import com.rodrilang.librarymanager.purchasing.order.model.PurchaseOrderStatus;

public record BookPurchaseRequirementStatusResponse(
        boolean pending,
        Long requirementId,
        Integer quantity,
        Integer orderedQuantity,
        Integer remainingQuantity,
        Long preferredProviderId,
        String preferredProviderName,

        BookReplenishmentState state,

        Long orderId,
        Long orderItemId,
        String orderNumber,
        PurchaseOrderStatus orderStatus,
        Integer orderQuantity,
        Integer orderRequirementQuantity
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
                preferredProviderName,
                BookReplenishmentState.NONE,
                null,
                null,
                null,
                null,
                0,
                0
        );
    }
}
