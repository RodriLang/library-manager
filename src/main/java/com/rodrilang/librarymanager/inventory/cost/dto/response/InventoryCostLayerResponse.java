package com.rodrilang.librarymanager.inventory.cost.dto.response;

import com.rodrilang.librarymanager.enums.BookCondition;
import com.rodrilang.librarymanager.inventory.cost.model.InventoryCostReferencePriceSource;
import com.rodrilang.librarymanager.inventory.cost.model.InventoryCostSourceType;
import com.rodrilang.librarymanager.inventory.cost.model.InventoryCostType;

import java.math.BigDecimal;
import java.time.Instant;

public record InventoryCostLayerResponse(

        Long id,

        Long inventoryId,

        Long bookId,

        String isbn,

        String title,

        BookCondition condition,

        Integer quantityReceived,

        Integer quantityRemaining,

        Integer quantityConsumed,

        InventoryCostType costType,

        BigDecimal unitCost,

        BigDecimal discountPercentage,

        BigDecimal referencePrice,

        InventoryCostReferencePriceSource referencePriceSource,

        InventoryCostSourceType sourceType,

        String sourceReferenceId,

        Instant enteredAt,

        Instant createdAt,

        Instant updatedAt

) {
}
