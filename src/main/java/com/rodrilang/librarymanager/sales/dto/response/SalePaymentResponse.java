package com.rodrilang.librarymanager.sales.dto.response;

import com.rodrilang.librarymanager.payment.model.PaymentMethod;

import java.math.BigDecimal;

public record SalePaymentResponse(

        Long id,
        PaymentMethod method,
        BigDecimal amount,
        String reference

) {
}
