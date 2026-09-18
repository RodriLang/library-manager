package com.rodrilang.librarymanager.inventory.valuation.repository;

import java.math.BigDecimal;

public record InventoryValuationSnapshot(

        long totalUnits,

        long currentPriceUnits,

        BigDecimal knownRetailValueAmount,

        long historicalCostUnits,

        BigDecimal knownHistoricalCostAmount,

        BigDecimal realHistoricalCostAmount,

        BigDecimal estimatedHistoricalCostAmount,

        long replacementCostUnits,

        BigDecimal knownReplacementCostAmount

) {
}
