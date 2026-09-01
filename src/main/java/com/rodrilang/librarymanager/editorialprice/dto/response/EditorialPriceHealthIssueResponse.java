package com.rodrilang.librarymanager.editorialprice.dto.response;

import com.rodrilang.librarymanager.editorialprice.enums.EditorialPriceHealthIssueType;

import java.math.BigDecimal;
import java.time.LocalDate;

public record EditorialPriceHealthIssueResponse(
        Long bookId,
        String title,
        String isbn,
        String publisherName,
        EditorialPriceHealthIssueType type,
        BigDecimal currentPrice,
        String currency,
        LocalDate currentValidFrom,
        LocalDate conflictValidFrom,
        LocalDate nextValidFrom,
        LocalDate lastEvidenceOn
) {
}