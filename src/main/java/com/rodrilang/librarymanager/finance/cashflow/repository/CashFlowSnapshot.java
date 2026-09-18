package com.rodrilang.librarymanager.finance.cashflow.repository;

import java.math.BigDecimal;
import java.util.List;

public record CashFlowSnapshot(

        BigDecimal salesReceiptsAmount,

        BigDecimal supplierPaymentsAmount,

        long salePaymentCount,

        long purchasePaymentCount,

        List<MethodAmount> saleAmountsByMethod,

        List<MethodAmount> purchaseAmountsByMethod

) {

    public record MethodAmount(
            String method,
            BigDecimal amount
    ) {
    }
}
