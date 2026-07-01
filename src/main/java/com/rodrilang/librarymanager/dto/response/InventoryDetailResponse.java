package com.rodrilang.librarymanager.dto.response;

import java.time.Instant;

public record InventoryDetailResponse(

        Long id,

        BookDetailResponse book,

        Integer stock,

        Integer minimumStock,

        Boolean active,

        Instant createdAt,

        Instant updatedAt

) {
}