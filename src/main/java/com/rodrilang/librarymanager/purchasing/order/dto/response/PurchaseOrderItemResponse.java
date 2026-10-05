package com.rodrilang.librarymanager.purchasing.order.dto.response;

import java.math.BigDecimal;

public record PurchaseOrderItemResponse(

        Long id,

        Long bookId,
        String isbn,
        String title,
        String coverUrl,

        Long requirementId,

        Integer quantity,

        Integer requirementQuantity,
        Integer additionalQuantity,

        Integer receivedQuantity,
        Integer pendingQuantity,

        BigDecimal unitPrice,
        BigDecimal subtotal,

        String notes

) {
}
