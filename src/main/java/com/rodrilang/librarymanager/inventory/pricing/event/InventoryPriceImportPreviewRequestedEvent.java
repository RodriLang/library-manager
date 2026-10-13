package com.rodrilang.librarymanager.inventory.pricing.event;

public record InventoryPriceImportPreviewRequestedEvent(
        Long importId,
        String sourcePath
) {
}
