package com.rodrilang.librarymanager.inventory.bulk.dto.response;

import com.rodrilang.librarymanager.inventory.bulk.model.InventoryBulkAction;

public record InventoryBulkActionResponse(

        Long operationId,

        InventoryBulkAction action,

        int selected,

        int affected,

        int skipped

) {
}