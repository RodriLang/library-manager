package com.rodrilang.librarymanager.economics.pending.dto.response;

import com.rodrilang.librarymanager.economics.pending.model.EconomicDataPendingReason;

import java.math.BigDecimal;
import java.util.List;

public record EconomicDataPendingItemResponse(

        Long bookId,

        String isbn,

        String title,

        Long stockUnits,

        Long unknownCostUnits,

        Long estimatedCostUnits,

        Long missingDiscountUnits,

        BigDecimal currentEditorialPrice,

        Boolean hasCommercialTerm,

        List<EconomicDataPendingReason> reasons

) {
}
