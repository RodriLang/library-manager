package com.rodrilang.librarymanager.dto.response;

import com.rodrilang.librarymanager.enums.BookCondition;

import java.math.BigDecimal;
import java.time.Instant;

public record InventoryDetailResponse(

        Long id,

        BookDetailResponse book,

        Integer stock,

        Integer minimumStock,

        BookCondition condition,

        BigDecimal salePrice,

        Boolean active,

        Instant createdAt,

        Instant updatedAt

) {
}