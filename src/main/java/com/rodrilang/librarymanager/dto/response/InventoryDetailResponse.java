package com.rodrilang.librarymanager.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record InventoryDetailResponse(

        Long id,

        BookDetailResponse book,

        Integer stock,

        BigDecimal salePrice,

        BigDecimal costPrice,

        Boolean active,

        Instant createdAt,

        Instant updatedAt

) {
}