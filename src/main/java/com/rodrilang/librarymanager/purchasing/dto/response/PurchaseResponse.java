package com.rodrilang.librarymanager.purchasing.dto.response;

import com.rodrilang.librarymanager.purchasing.model.PurchaseStatus;
import com.rodrilang.librarymanager.purchasing.payment.dto.response.PurchasePaymentResponse;
import com.rodrilang.librarymanager.purchasing.payment.model.PurchasePaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PurchaseResponse(
        Long id,
        PurchaseProviderResponse provider,
        LocalDate purchaseDate,
        String documentNumber,
        PurchaseStatus status,
        BigDecimal totalAmount,
        BigDecimal paidAmount,
        BigDecimal pendingAmount,
        PurchasePaymentStatus paymentStatus,
        String notes,
        List<PurchaseItemResponse> items,
        List<PurchasePaymentResponse> payments
) {
}
