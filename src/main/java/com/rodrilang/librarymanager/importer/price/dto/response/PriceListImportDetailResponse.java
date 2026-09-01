package com.rodrilang.librarymanager.importer.price.dto.response;

import com.rodrilang.librarymanager.importer.price.enums.PriceListImportPhase;
import com.rodrilang.librarymanager.importer.price.model.PriceListImportJobStatus;

import java.time.Instant;
import java.time.LocalDate;

public record PriceListImportDetailResponse(

        Long id,

        Long providerId,

        String providerName,

        String originalFileName,

        LocalDate validFrom,

        PriceListImportJobStatus status,

        PriceListImportPhase phase,

        Instant createdAt,

        Instant startedAt,

        Instant finishedAt,

        int totalRows,

        int processedRows,

        int processedBooks,

        int duplicateBookRows,

        int processedPrices,

        int createdBooks,

        int createdPrices,

        int updatedPrices,

        int unchangedPrices,

        int skippedRows,

        int errorCount,

        String errorMessage,

        int firstPrices,

        int increasedPrices,

        int decreasedPrices,

        int maintainedPrices,

        boolean itemHistoryAvailable
) {
}