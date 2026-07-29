package com.rodrilang.librarymanager.integrations.tiendanube.dto.response;

import com.rodrilang.librarymanager.enums.BookCondition;

public record InventoryMatchCandidateResponse(

        Long inventoryId,

        Long bookId,

        String isbn,

        String title,

        String authors,

        String publisher,

        BookCondition condition,

        Integer stock

) {
}