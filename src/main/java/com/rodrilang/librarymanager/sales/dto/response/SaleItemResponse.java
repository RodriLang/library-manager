package com.rodrilang.librarymanager.sales.dto.response;

import java.math.BigDecimal;

public record SaleItemResponse(

        Long id,
        Long inventoryId,
        Long bookId,
        String isbn,
        String description,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal

) {
}
