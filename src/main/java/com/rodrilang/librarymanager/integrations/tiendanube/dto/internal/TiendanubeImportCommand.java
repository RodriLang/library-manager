package com.rodrilang.librarymanager.integrations.tiendanube.dto.internal;

import com.rodrilang.librarymanager.enums.BookCondition;

import java.math.BigDecimal;

public record TiendanubeImportCommand(
        Long bookstoreId,
        Long bookId,
        Long storeId,
        BookCondition condition,
        Integer stock,
        BigDecimal salePrice
) {
}