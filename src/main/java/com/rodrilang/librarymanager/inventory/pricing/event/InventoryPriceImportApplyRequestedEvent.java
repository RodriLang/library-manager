package com.rodrilang.librarymanager.inventory.pricing.event;

public record InventoryPriceImportApplyRequestedEvent(
        Long importId,
        Long userId
) {
}
