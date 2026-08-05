package com.rodrilang.librarymanager.importer.price.configuration.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ProviderBookReconciliationPreview(

        ProviderBookSummary providerBook,

        BookSummary currentBook,

        BookSummary targetBook,

        int pricesToMove,

        boolean targetAlreadyLinkedToProvider,

        Long existingTargetProviderBookId,

        List<PriceConflict> priceConflicts,

        boolean canConfirm,

        List<String> warnings

) {

    public record ProviderBookSummary(
            Long id,
            Long providerId,
            String providerName,
            String externalCode,
            String reportedIsbn,
            String identifierStatus
    ) {
    }

    public record BookSummary(
            Long id,
            String title,
            String isbn10,
            String isbn13,
            String publisherName,
            boolean hasInventory
    ) {
    }

    public record PriceConflict(
            Long sourcePriceId,
            Long targetPriceId,
            LocalDate validFrom,
            BigDecimal sourcePrice,
            BigDecimal targetPrice
    ) {
    }
}