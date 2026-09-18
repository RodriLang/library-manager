package com.rodrilang.librarymanager.sales.dto.response;

import com.rodrilang.librarymanager.sales.model.SaleOrigin;
import com.rodrilang.librarymanager.sales.model.SaleStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record SaleResponse(

        Long id,
        SaleStatus status,
        SaleOrigin origin,
        Integer itemCount,
        Integer totalUnits,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal total,
        Instant soldAt,
        Long createdByUserId,
        String createdByName

) {
}
