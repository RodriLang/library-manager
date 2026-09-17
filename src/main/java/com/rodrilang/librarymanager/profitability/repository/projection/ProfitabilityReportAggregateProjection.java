package com.rodrilang.librarymanager.profitability.repository.projection;

import java.math.BigDecimal;

public interface ProfitabilityReportAggregateProjection {

    Long getSaleCount();

    Long getTotalUnits();

    BigDecimal getNetSalesAmount();

    BigDecimal getRealCostAmount();

    BigDecimal getEstimatedCostAmount();

    Long getRealCostUnits();

    Long getEstimatedCostUnits();
}
