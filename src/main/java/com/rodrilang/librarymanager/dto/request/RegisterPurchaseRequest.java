package com.rodrilang.librarymanager.dto.request;

import java.util.List;

public record RegisterPurchaseRequest(

        Long supplierId,

        String invoiceNumber,

        List<PurchaseItemRequest> items
) {
}