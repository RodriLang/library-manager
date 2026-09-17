package com.rodrilang.librarymanager.purchasing.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BookSupplierTermResponse(
        Long id,
        Long supplierId,
        Long bookId,
        String isbn,
        String title,
        BigDecimal discountPercentage,
        LocalDate lastPurchaseDate
) {
}
