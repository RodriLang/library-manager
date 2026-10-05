package com.rodrilang.librarymanager.purchasing.order.dto.request;

import jakarta.validation.constraints.Size;

public record UpdatePurchaseOrderItemNotesRequest(

        @Size(max = 500)
        String notes

) {
}
