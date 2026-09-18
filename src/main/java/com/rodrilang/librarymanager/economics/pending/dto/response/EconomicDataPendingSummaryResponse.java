package com.rodrilang.librarymanager.economics.pending.dto.response;

public record EconomicDataPendingSummaryResponse(

        Long totalStockUnits,

        Long unknownCostUnits,

        Long estimatedCostUnits,

        Long missingDiscountUnits,

        Long missingCurrentPriceUnits,

        Long pendingBookCount,

        Long booksWithoutCommercialTerms

) {
}
