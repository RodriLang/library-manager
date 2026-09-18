package com.rodrilang.librarymanager.profitability.dto.response;

import com.rodrilang.librarymanager.profitability.model.CostCoverageStatus;

import java.math.BigDecimal;

public record ProfitabilitySummaryResponse(

        BigDecimal netSalesAmount,

        BigDecimal knownCostAmount,
        BigDecimal realCostAmount,
        BigDecimal estimatedCostAmount,

        Integer totalUnits,
        Integer realCostUnits,
        Integer estimatedCostUnits,
        Integer knownCostUnits,
        Integer unknownCostUnits,
        BigDecimal costCoveragePercentage,
        CostCoverageStatus costCoverageStatus,

        BigDecimal grossProfitAmount,
        BigDecimal grossMarginPercentage

) {
}
