package com.rodrilang.librarymanager.dto.internal;

public record InventoryStockSummaryCounts(

        long total,

        long available,

        long lowStock,

        long outOfStock

) {
}