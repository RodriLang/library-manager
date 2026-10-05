package com.rodrilang.librarymanager.purchasing.receipt.dto.response;

import com.rodrilang.librarymanager.purchasing.receipt.model.GoodsReceiptSource;
import com.rodrilang.librarymanager.purchasing.receipt.model.GoodsReceiptStatus;
import java.time.Instant;

public record GoodsReceiptResponse(
        Long id,
        String receiptNumber,
        GoodsReceiptSource source,
        GoodsReceiptStatus status,
        Long providerId,
        String providerName,
        Long purchaseOrderId,
        String purchaseOrderNumber,
        String documentType,
        String documentNumber,
        Integer itemCount,
        Integer totalUnits,
        Instant createdAt,
        Instant confirmedAt
) {}
