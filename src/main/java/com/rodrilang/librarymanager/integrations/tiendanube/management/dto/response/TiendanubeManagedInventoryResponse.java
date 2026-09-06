package com.rodrilang.librarymanager.integrations.tiendanube.management.dto.response;

import com.rodrilang.librarymanager.integrations.tiendanube.enums.TiendanubeInventoryStatus;
import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobStatus;
import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record TiendanubeManagedInventoryResponse(
        Long inventoryId,
        Long bookId,
        String title,
        List<String> authorNames,
        Long publisherId,
        String publisherName,
        String isbn,
        String coverUrl,
        Integer stock,
        Integer minimumStock,
        BigDecimal salePrice,
        String condition,
        TiendanubeInventoryStatus status,
        boolean published,
        boolean priceSyncEnabled,
        Long productId,
        Long variantId,
        String sku,
        Instant lastSyncedAt,
        String lastError,
        Long latestJobId,
        TiendanubeJobType latestJobType,
        TiendanubeJobStatus latestJobStatus,
        String latestJobErrorType,
        String latestJobErrorMessage,
        Instant latestJobCreatedAt
) {
}
