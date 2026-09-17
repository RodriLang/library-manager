package com.rodrilang.librarymanager.purchasing.dto.response;

import com.rodrilang.librarymanager.purchasing.model.PurchaseStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PurchaseResponse(
        Long id,
        SupplierResponse supplier,
        LocalDate purchaseDate,
        String documentNumber,
        PurchaseStatus status,
        BigDecimal totalAmount,
        String notes,
        List<PurchaseItemResponse> items
) {
}
