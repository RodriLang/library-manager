package com.rodrilang.librarymanager.dto.response;

import com.rodrilang.librarymanager.enums.BookCondition;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record InventorySummaryResponse(
        Long id,
        Long bookId,
        String isbn,
        String title,
        List<String> authorNames,
        String publisherName,
        String coverUrl,
        Integer stock,
        Integer minimumStock,
        BookCondition condition,
        BigDecimal salePrice,
        LocalDate currentPriceEffectiveFrom,
        LocalDate currentPriceLastConfirmedAt,
        String currentPriceLastConfirmedSource,
        BigDecimal nextSalePrice,
        LocalDate nextPriceEffectiveFrom,
        LocalDate lastPriceCheckedAt,
        Boolean active
) {
}
