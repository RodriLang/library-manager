package com.rodrilang.librarymanager.inventory.count.dto.response;

public record InventoryCountSummaryResponse(

        long totalItems,

        long totalUnits,

        long resolvedItems,

        long pendingCatalogItems,

        long pendingPriceItems,

        long invalidItems,

        long supersededItems,

        long appliedItems

) {
}
