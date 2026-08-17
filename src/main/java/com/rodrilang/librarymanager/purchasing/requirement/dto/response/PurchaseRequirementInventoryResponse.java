package com.rodrilang.librarymanager.purchasing.requirement.dto.response;

public record PurchaseRequirementInventoryResponse(

        Long inventoryId,
        boolean exists,
        Integer stock,
        Integer minimumStock

) {
}