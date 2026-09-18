package com.rodrilang.librarymanager.purchasing.dto.response;

import com.rodrilang.librarymanager.enums.BookCondition;

import java.math.BigDecimal;

public record PurchaseItemResponse(
        Long id,
        Long bookId,
        String isbn,
        String title,
        BookCondition condition,
        Integer quantity,
        BigDecimal editorialPriceSnapshot,
        BigDecimal discountPercentage,
        BigDecimal unitCost,
        BigDecimal totalCost
) {
}
