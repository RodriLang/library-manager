package com.rodrilang.librarymanager.purchasing.payment.dto.response;

import com.rodrilang.librarymanager.payment.model.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;

public record PurchasePaymentResponse(
        Long id,
        Instant paidAt,
        PaymentMethod method,
        BigDecimal amount,
        String reference,
        String notes,
        boolean active,
        Instant cancelledAt,
        String cancellationReason,
        Instant createdAt
) {
}
