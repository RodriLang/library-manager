package com.rodrilang.librarymanager.finance.cashflow.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CashFlowReportResponse(

        Instant from,

        Instant to,

        BigDecimal salesReceiptsAmount,

        BigDecimal supplierPaymentsAmount,

        BigDecimal netCashFlowAmount,

        Long salePaymentCount,

        Long purchasePaymentCount,

        List<CashFlowByMethodResponse> byMethod

) {
}
