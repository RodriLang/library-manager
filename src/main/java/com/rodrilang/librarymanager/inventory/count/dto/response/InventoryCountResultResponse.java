package com.rodrilang.librarymanager.inventory.count.dto.response;

import com.rodrilang.librarymanager.inventory.count.model.InventoryCountDifferenceType;

import java.time.Instant;

public record InventoryCountResultResponse(

        Long id,

        InventoryCountBookResponse book,

        Long inventoryId,

        boolean baseline,

        boolean inventoryExistedBefore,

        boolean previousActive,

        Integer previousQuantity,

        Integer countedQuantity,

        Integer appliedDelta,

        Integer resultingQuantity,

        Boolean resultingActive,

        InventoryCountDifferenceType differenceType,

        Instant appliedAt,

        Instant revertedAt

) {
}
