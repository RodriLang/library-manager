package com.rodrilang.librarymanager.purchasing.payment.dto.request;

import com.rodrilang.librarymanager.payment.model.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public record CreatePurchasePaymentRequest(
        Instant paidAt,

        @NotNull
        PaymentMethod method,

        @NotNull
        @DecimalMin(value = "0.01")
        @Digits(integer = 14, fraction = 2)
        BigDecimal amount,

        @Size(max = 100)
        String reference,

        @Size(max = 1000)
        String notes
) {
}
