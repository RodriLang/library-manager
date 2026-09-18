package com.rodrilang.librarymanager.economics.pending.repository;

public record EconomicDataPendingSummary(

        long totalStockUnits,

        long unknownCostUnits,

        long estimatedCostUnits,

        long missingDiscountUnits,

        long missingCurrentPriceUnits,

        long pendingBookCount,

        long booksWithoutCommercialTerms

) {
}
