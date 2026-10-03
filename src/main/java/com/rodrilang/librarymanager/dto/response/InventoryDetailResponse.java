package com.rodrilang.librarymanager.dto.response;

import com.rodrilang.librarymanager.enums.BookCondition;
import com.rodrilang.librarymanager.integrations.tiendanube.enums.TiendanubeInventoryStatus;
import com.rodrilang.librarymanager.purchasing.preference.dto.response.PreferredProviderResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record InventoryDetailResponse(
        Long id,
        BookDetailResponse book,
        PreferredProviderResponse preferredProvider,
        Integer stock,
        Integer minimumStock,
        BookCondition condition,
        BigDecimal salePrice,
        LocalDate currentPriceEffectiveFrom,
        LocalDate currentPriceLastConfirmedAt,
        String currentPriceLastConfirmedSource,
        BigDecimal nextSalePrice,
        LocalDate nextPriceEffectiveFrom,
        LocalDate lastPriceCheckedAt,
        Boolean tiendanubePriceSyncEnabled,
        TiendanubeInventoryStatus tiendanubeStatus,
        Boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
