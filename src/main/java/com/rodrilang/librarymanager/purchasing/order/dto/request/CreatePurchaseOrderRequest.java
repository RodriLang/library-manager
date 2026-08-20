package com.rodrilang.librarymanager.purchasing.order.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreatePurchaseOrderRequest(

        @NotNull
        Long providerId,

        @Size(max = 1000)
        String notes

) {
}