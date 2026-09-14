package com.rodrilang.librarymanager.inventory.count.dto.request;

import com.rodrilang.librarymanager.enums.BookCondition;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountMode;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountPurpose;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateInventoryCountRequest(

        @NotNull
        InventoryCountMode mode,

        @NotNull
        InventoryCountPurpose purpose,

        BookCondition condition,

        @Size(max = 500)
        String notes

) {
}
