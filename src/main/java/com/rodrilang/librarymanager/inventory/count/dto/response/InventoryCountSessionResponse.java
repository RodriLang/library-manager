package com.rodrilang.librarymanager.inventory.count.dto.response;

import com.rodrilang.librarymanager.enums.BookCondition;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountMode;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountPurpose;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountStatus;

import java.time.Instant;

public record InventoryCountSessionResponse(

        Long id,

        InventoryCountMode mode,

        InventoryCountPurpose purpose,

        InventoryCountStatus status,

        BookCondition condition,

        String notes,

        InventoryCountSummaryResponse summary,

        Instant baselineAt,

        Instant reviewedAt,

        Instant appliedAt,

        Instant revertedAt,

        Instant createdAt,

        Instant updatedAt

) {
}