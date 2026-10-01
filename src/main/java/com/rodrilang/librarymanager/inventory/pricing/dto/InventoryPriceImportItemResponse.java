package com.rodrilang.librarymanager.inventory.pricing.dto;

import com.rodrilang.librarymanager.inventory.pricing.model.InventoryPriceImportClassification;

import java.math.BigDecimal;

public record InventoryPriceImportItemResponse(
        Long id,
        Long inventoryId,
        Long bookId,
        Integer rowNumber,
        String isbn,
        String title,
        String author,
        BigDecimal incomingPrice,
        BigDecimal currentPrice,
        BigDecimal existingScheduledPrice,
        BigDecimal changePercent,
        InventoryPriceImportClassification classification,
        String conflictReason,
        boolean selectedDefault
) {
}
