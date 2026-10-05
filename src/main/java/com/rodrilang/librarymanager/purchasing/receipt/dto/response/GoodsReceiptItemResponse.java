package com.rodrilang.librarymanager.purchasing.receipt.dto.response;

import com.rodrilang.librarymanager.enums.BookCondition;

public record GoodsReceiptItemResponse(
        Long id,
        Long bookId,
        String isbn,
        String title,
        String coverUrl,
        Long purchaseOrderItemId,
        BookCondition condition,
        Integer expectedQuantity,
        Integer documentQuantity,
        Integer scannedQuantity,
        Integer receivedQuantity,
        Integer differenceFromOrder,
        Integer differenceFromDocument,
        String notes
) {}
