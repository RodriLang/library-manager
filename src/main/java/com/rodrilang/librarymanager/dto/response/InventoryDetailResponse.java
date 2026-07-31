package com.rodrilang.librarymanager.dto.response;

import com.rodrilang.librarymanager.enums.BookCondition;
import com.rodrilang.librarymanager.integrations.tiendanube.enums.TiendanubeInventoryStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record InventoryDetailResponse(

        Long id,

        BookDetailResponse book,

        Integer stock,

        Integer minimumStock,

        BookCondition condition,

        BigDecimal salePrice,

        Boolean editorialPriceSyncEnabled,

        Boolean tiendanubePriceSyncEnabled,

        TiendanubeInventoryStatus tiendanubeStatus,

        Boolean active,

        Instant createdAt,

        Instant updatedAt

) {
}