package com.rodrilang.librarymanager.inventory.valuation.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InventoryValuationResponse(

        LocalDate asOf,

        Long totalUnits,

        BigDecimal knownRetailValueAmount,

        ValuationCoverageResponse retailValueCoverage,

        BigDecimal knownHistoricalCostAmount,

        BigDecimal realHistoricalCostAmount,

        BigDecimal estimatedHistoricalCostAmount,

        ValuationCoverageResponse historicalCostCoverage,

        BigDecimal knownReplacementCostAmount,

        ValuationCoverageResponse replacementCostCoverage

) {
}
