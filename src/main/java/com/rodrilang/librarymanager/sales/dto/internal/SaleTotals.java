package com.rodrilang.librarymanager.sales.dto.internal;

import java.math.BigDecimal;

public record SaleTotals(

        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal total

) {
}
