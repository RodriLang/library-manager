package com.rodrilang.librarymanager.integrations.tiendanube.dto.response;

import java.math.BigDecimal;

public record TiendanubeImportBookCandidateResponse(
        Long bookId,
        String isbn,
        String title,
        String authors,
        String publisher,
        BigDecimal salePrice,
        Long inventoryId,
        boolean inventoryLinked
) {
}