package com.rodrilang.librarymanager.integrations.tiendanube.dto.response;

public record TiendanubeImportBookCandidateResponse(
        Long bookId,
        String isbn,
        String title,
        String authors,
        String publisher,
        Long inventoryId,
        boolean inventoryLinked
) {
}