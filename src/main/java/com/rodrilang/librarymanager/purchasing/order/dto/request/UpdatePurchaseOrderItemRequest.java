package com.rodrilang.librarymanager.purchasing.order.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record UpdatePurchaseOrderItemRequest(

        @NotNull
        @Positive
        Integer quantity

) {
}