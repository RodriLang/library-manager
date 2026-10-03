package com.rodrilang.librarymanager.inventory.pricing.dto;

import java.math.BigDecimal;

public record InventoryPriceImportDuplicateRowResponse(
        Long itemId,
        Integer rowNumber,
        BigDecimal incomingPrice
) {
}