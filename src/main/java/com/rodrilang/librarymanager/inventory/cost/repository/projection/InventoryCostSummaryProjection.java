package com.rodrilang.librarymanager.inventory.cost.repository.projection;

public interface InventoryCostSummaryProjection {

    Long getCurrentUnits();

    Long getKnownCostUnits();

    Long getUnknownCostUnits();

    Long getRealCostUnits();

    Long getEstimatedCostUnits();

    Long getUnknownCostLayers();

    Long getMissingDiscountLayers();
}
