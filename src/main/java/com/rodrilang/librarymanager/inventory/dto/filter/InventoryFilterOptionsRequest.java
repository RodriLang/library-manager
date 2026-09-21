package com.rodrilang.librarymanager.inventory.dto.filter;

import java.util.List;

public record InventoryFilterOptionsRequest(
        List<Long> authorIds,
        List<Long> publisherIds
) {
}