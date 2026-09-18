package com.rodrilang.librarymanager.economics.pending.repository;

import java.math.BigDecimal;

public record EconomicDataPendingRow(

        Long bookId,

        String isbn,

        String title,

        long stockUnits,

        long unknownCostUnits,

        long estimatedCostUnits,

        long missingDiscountUnits,

        BigDecimal currentEditorialPrice,

        boolean hasCommercialTerm

) {
}
