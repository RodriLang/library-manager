package com.rodrilang.librarymanager.sales.dto.response;

import com.rodrilang.librarymanager.profitability.dto.response.ProfitabilitySummaryResponse;
import com.rodrilang.librarymanager.sales.model.SaleOrigin;
import com.rodrilang.librarymanager.sales.model.SaleStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record SaleDetailResponse(

        Long id,
        SaleStatus status,
        SaleOrigin origin,
        Instant soldAt,

        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal total,

        String externalReference,
        String notes,

        Long createdByUserId,
        String createdByName,

        Instant cancelledAt,
        Long cancelledByUserId,
        String cancelledByName,
        String cancellationReason,

        Instant createdAt,
        Instant updatedAt,

        ProfitabilitySummaryResponse profitability,

        List<SaleItemResponse> items,
        List<SalePaymentResponse> payments

) {
}
