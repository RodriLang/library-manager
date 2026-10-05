package com.rodrilang.librarymanager.purchasing.receipt.dto.response;

import com.rodrilang.librarymanager.purchasing.receipt.model.GoodsReceiptSource;
import com.rodrilang.librarymanager.purchasing.receipt.model.GoodsReceiptStatus;
import java.time.Instant;
import java.util.List;

public record GoodsReceiptDetailResponse(
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
        String notes,
        Integer itemCount,
        Integer totalUnits,
        Instant createdAt,
        Instant confirmedAt,
        List<GoodsReceiptItemResponse> items
) {}
