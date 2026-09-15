package com.rodrilang.librarymanager.sales.dto.request;

import com.rodrilang.librarymanager.sales.model.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateSalePaymentRequest(

        @NotNull
        PaymentMethod method,

        @NotNull
        @DecimalMin(value = "0.01")
        @Digits(integer = 12, fraction = 2)
        BigDecimal amount,

        @Size(max = 100)
        String reference

) {
}
