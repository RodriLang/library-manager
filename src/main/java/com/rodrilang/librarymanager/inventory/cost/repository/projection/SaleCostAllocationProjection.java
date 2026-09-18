package com.rodrilang.librarymanager.inventory.cost.repository.projection;

import com.rodrilang.librarymanager.inventory.cost.model.InventoryCostType;

import java.math.BigDecimal;

public interface SaleCostAllocationProjection {

    Integer getQuantity();

    InventoryCostType getCostType();

    BigDecimal getUnitCost();
}
