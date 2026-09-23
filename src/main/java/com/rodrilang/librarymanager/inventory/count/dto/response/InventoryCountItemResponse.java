package com.rodrilang.librarymanager.inventory.count.dto.response;

import com.rodrilang.librarymanager.inventory.count.model.InventoryCountItemStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record InventoryCountItemResponse(
        Long id,
        String rawIdentifier,
        String normalizedIdentifier,
        String isbn10,
        String isbn13,
        Integer quantity,
        InventoryCountItemStatus status,
        BigDecimal salePriceOverride,
        BigDecimal editorialPrice,
        BigDecimal effectiveSalePrice,
        String priceSource,
        boolean inventoryExists,
        Boolean editorialPriceSyncEnabled,
        Boolean publishOnTiendanube,
        Boolean tiendanubePriceSyncEnabled,
        Integer minimumStock,
        InventoryCountBookResponse book,
        Long catalogCandidateId,
        Instant firstScannedAt,
        Instant lastScannedAt,
        Instant appliedAt
) {
}
