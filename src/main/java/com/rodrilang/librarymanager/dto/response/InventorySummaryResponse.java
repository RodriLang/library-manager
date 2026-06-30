package com.rodrilang.librarymanager.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record InventorySummaryResponse(

        Long id,

        Long bookId,

        String isbn,

        String title,

        List<String> authorNames,

        String publisherName,

        String thumbnailUrl,

        Integer stock,

        BigDecimal salePrice,

        Boolean active

) {
}