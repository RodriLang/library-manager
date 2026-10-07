package com.rodrilang.librarymanager.inventory.consignment.dto;

import com.rodrilang.librarymanager.enums.InventoryMovementSource;
import java.time.Instant;

public record ConsignmentSaleResponse(
        Long movementId,
        Long inventoryId,
        Long bookId,
        String isbn,
        String title,
        String coverUrl,
        Integer quantity,
        Long providerId,
        String providerName,
        InventoryMovementSource source,
        String referenceId,
        Instant soldAt,
        boolean settled,
        Long settlementId,
        String settlementNumber
) {}
