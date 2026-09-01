package com.rodrilang.librarymanager.editorialprice.dto.response;

import com.rodrilang.librarymanager.editorialprice.enums.EditorialPriceResolutionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record EditorialPriceResolutionListResponse(
        Long id,
        Long bookId,
        String title,
        String isbn,
        LocalDate validFrom,
        Long selectedEditorialPriceId,
        BigDecimal resolvedPrice,
        String resolvedCurrency,
        EditorialPriceResolutionType resolutionType,
        String sourceName,
        String note,
        String resolvedByUsername,
        Instant createdAt,
        boolean active,
        Instant deactivatedAt,
        String deactivatedByUsername,
        String deactivationNote
) {
}