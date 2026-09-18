package com.rodrilang.librarymanager.inventory.count.dto.request;

import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record UpdateInventoryCountItemRequest(

        @Positive
        Integer quantity,

        @Positive
        BigDecimal salePrice

) {
}
