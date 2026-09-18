package com.rodrilang.librarymanager.inventory.count.event;

import java.util.Set;

public record InventoryCountStockChangedEvent(

        Set<Long> inventoryIds

) {
}
