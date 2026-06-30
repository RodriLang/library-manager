package com.rodrilang.librarymanager.dto.request;

import java.math.BigDecimal;

public record RegisterManualBookPurchaseRequest(

        BookRequest book,

        Integer quantity,

        BigDecimal costPrice,

        BigDecimal salePrice
) {
}