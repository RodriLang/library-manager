package com.rodrilang.librarymanager.inventory.cost.dto.response;

import java.math.BigDecimal;

public record InventoryCostSummaryResponse(

        long currentUnits,

        long knownCostUnits,

        long unknownCostUnits,

        long realCostUnits,

        long estimatedCostUnits,

        long unknownCostLayers,

        long missingDiscountLayers,

        BigDecimal currentCostCoveragePercentage

) {
}
