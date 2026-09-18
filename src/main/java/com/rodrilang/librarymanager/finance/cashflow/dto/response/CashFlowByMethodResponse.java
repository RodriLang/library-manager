package com.rodrilang.librarymanager.finance.cashflow.dto.response;

import com.rodrilang.librarymanager.payment.model.PaymentMethod;

import java.math.BigDecimal;

public record CashFlowByMethodResponse(

        PaymentMethod method,

        BigDecimal inflowAmount,

        BigDecimal outflowAmount,

        BigDecimal netAmount

) {
}
