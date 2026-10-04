package com.rodrilang.librarymanager.inventory.pricing.dto;

import java.util.List;

public record ApplyInventoryPriceImportRequest(
        List<Long> itemIds
) {
}
