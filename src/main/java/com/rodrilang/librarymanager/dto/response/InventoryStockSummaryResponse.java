package com.rodrilang.librarymanager.dto.response;

public record InventoryStockSummaryResponse(
        long total,
        long available,
        long low,
        long out
) {
}