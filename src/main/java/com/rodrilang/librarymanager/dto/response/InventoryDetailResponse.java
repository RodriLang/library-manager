package com.rodrilang.librarymanager.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

public record InventoryDetailResponse(

        Long id,

        Long bookId,

        String isbn,

        String title,

        String publisherName,

        Set<AuthorResponse> authors,

        Integer stock,

        BigDecimal salePrice,

        Boolean active,

        Instant createdAt,

        Instant updatedAt

) {
}