package com.rodrilang.librarymanager.inventory.bulk.dto.request;

import com.rodrilang.librarymanager.enums.BookCondition;
import com.rodrilang.librarymanager.enums.InventoryStockFilter;

public record InventoryBulkFilterRequest(

        String q,

        BookCondition condition,

        Boolean active,

        Long publisherId,

        InventoryStockFilter stock

) {
}