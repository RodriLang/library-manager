package com.rodrilang.librarymanager.integrations.tiendanube.management.dto.response;

import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobStatus;
import com.rodrilang.librarymanager.integrations.tiendanube.management.enums.TiendanubeBulkItemStatus;

import java.time.Instant;

public record TiendanubeBulkOperationItemResponse(
        Long id,
        Long inventoryId,
        String title,
        String isbn,
        TiendanubeBulkItemStatus dispatchStatus,
        Long jobId,
        TiendanubeJobStatus jobStatus,
        Integer attemptCount,
        Integer maxAttempts,
        String errorType,
        String errorMessage,
        String skipReason,
        String skipMessage,
        Instant createdAt,
        Instant dispatchedAt,
        Instant completedAt
) {
}
