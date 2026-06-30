package com.rodrilang.librarymanager.dto.response;

import java.math.BigDecimal;

public record InventorySummaryResponse(

        Long id,

        Long bookId,

        String isbn,

        String title,

        Integer stock,

        BigDecimal salePrice,

        Boolean active

) {
}