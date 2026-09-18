package com.rodrilang.librarymanager.inventory.cost.dto.request;

import com.rodrilang.librarymanager.inventory.cost.model.InventoryCostType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

public record SetInventoryCostRequest(

        InventoryCostType costType,

        @DecimalMin(value = "0.00")
        BigDecimal unitCost,

        @DecimalMin(value = "0.00")
        @DecimalMax(value = "100.00")
        BigDecimal discountPercentage

) {
}
