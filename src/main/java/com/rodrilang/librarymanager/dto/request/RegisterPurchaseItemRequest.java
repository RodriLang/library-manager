package com.rodrilang.librarymanager.dto.request;

import java.math.BigDecimal;

public record RegisterPurchaseItemRequest(

        String isbn,

        Integer quantity,

        BigDecimal costPrice,

        BigDecimal salePrice
) {
}
