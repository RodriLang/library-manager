package com.rodrilang.librarymanager.inventory.count.dto.internal;

import com.rodrilang.librarymanager.inventory.count.model.InventoryCountItemStatus;

import java.time.Instant;

public record InventoryCountItemUpsertCommand(

        Long sessionId,

        String rawIdentifier,

        String normalizedIdentifier,

        String isbn10,

        String isbn13,

        Long bookId,

        Long catalogCandidateId,

        InventoryCountItemStatus status,

        Instant scannedAt

) {
}
