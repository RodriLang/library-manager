package com.rodrilang.librarymanager.integrations.tiendanube.management.dto.response;

import com.rodrilang.librarymanager.integrations.tiendanube.management.enums.TiendanubeBulkAction;
import com.rodrilang.librarymanager.integrations.tiendanube.management.enums.TiendanubeBulkOperationStatus;
import com.rodrilang.librarymanager.integrations.tiendanube.management.enums.TiendanubeBulkSelectionType;

import java.time.Instant;

public record TiendanubeBulkOperationResponse(
        Long id,
        TiendanubeBulkAction action,
        TiendanubeBulkOperationStatus status,
        TiendanubeBulkSelectionType selectionType,
        String selectionDescription,
        long totalItems,
        long pendingItems,
        long queuedItems,
        long processingItems,
        long retryingItems,
        long completedItems,
        long failedItems,
        long blockedItems,
        long cancelledItems,
        long skippedItems,
        Instant createdAt,
        Instant cancelRequestedAt,
        Instant lastActivityAt
) {

    public static TiendanubeBulkOperationStatus resolveStatus(
            Instant cancelRequestedAt,
            long pendingItems,
            long queuedItems,
            long processingItems,
            long retryingItems,
            long completedItems,
            long failedItems,
            long blockedItems,
            long cancelledItems,
            long skippedItems
    ) {
        long active = pendingItems + queuedItems + processingItems + retryingItems;

        if (cancelRequestedAt != null && active > 0) {
            return TiendanubeBulkOperationStatus.CANCEL_REQUESTED;
        }

        if (active > 0) {
            return TiendanubeBulkOperationStatus.RUNNING;
        }

        if (cancelRequestedAt != null
                && completedItems == 0
                && failedItems == 0
                && blockedItems == 0
                && skippedItems == 0) {
            return TiendanubeBulkOperationStatus.CANCELLED;
        }

        if (failedItems > 0 || blockedItems > 0 || cancelledItems > 0 || skippedItems > 0) {
            return TiendanubeBulkOperationStatus.COMPLETED_WITH_ISSUES;
        }

        return TiendanubeBulkOperationStatus.COMPLETED;
    }
}
