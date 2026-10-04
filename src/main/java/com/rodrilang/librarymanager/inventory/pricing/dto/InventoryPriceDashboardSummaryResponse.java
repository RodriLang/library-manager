package com.rodrilang.librarymanager.inventory.pricing.dto;

public record InventoryPriceDashboardSummaryResponse(
        long total,
        long withPrice,
        long withoutPrice,
        long stale,
        long confirmedThisMonth,
        long notConfirmedThisMonth,
        long scheduled,
        long increased,
        long decreased,
        long reviewRequired
) {
}
