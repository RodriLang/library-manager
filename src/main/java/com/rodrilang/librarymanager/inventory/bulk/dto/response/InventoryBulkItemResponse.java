package com.rodrilang.librarymanager.inventory.bulk.dto.response;

import com.rodrilang.librarymanager.enums.BookCondition;
import com.rodrilang.librarymanager.integrations.tiendanube.enums.TiendanubeInventoryStatus;

import java.math.BigDecimal;

public record InventoryBulkItemResponse(

        Long id,

        Long bookId,

        String title,

        String isbn,

        String publisher,

        String coverUrl,

        BookCondition condition,

        Integer stock,

        Integer minimumStock,

        BigDecimal salePrice,

        Boolean active,

        Boolean editorialPriceSyncEnabled,

        Boolean tiendanubePriceSyncEnabled,

        TiendanubeInventoryStatus tiendanubeStatus

) {
}