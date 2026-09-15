package com.rodrilang.librarymanager.inventory.bulk.dto.response;

public record InventoryBulkPreviewResponse(

        long selected,

        long active,

        long inactive,

        long withStock,

        long withoutStock,

        long newItems,

        long usedItems,

        long linkedToTiendanube

) {
}