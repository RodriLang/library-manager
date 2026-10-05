package com.rodrilang.librarymanager.purchasing.order.dto.request;

import jakarta.validation.constraints.Size;

public record UpdatePurchaseOrderRequest(

        @Size(max = 1000)
        String notes

) {
}
