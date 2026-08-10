package com.rodrilang.librarymanager.dto.internal;

import java.util.List;

public record InventoryEditorialPriceSyncResult(
        int updatedInventories,
        List<Long> tiendanubeSyncInventoryIds
) {
}