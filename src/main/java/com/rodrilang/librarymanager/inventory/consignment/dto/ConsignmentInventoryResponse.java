package com.rodrilang.librarymanager.inventory.consignment.dto;

import com.rodrilang.librarymanager.enums.BookCondition;

public record ConsignmentInventoryResponse(
        Long inventoryId,
        Long bookId,
        String isbn,
        String title,
        String coverUrl,
        BookCondition condition,
        Integer stock,
        Integer consignmentStock,
        Integer ownedStock,
        Long providerId,
        String providerName
) {}
