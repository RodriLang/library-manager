package com.rodrilang.librarymanager.importer.price.dto.response;

import com.rodrilang.librarymanager.importer.price.enums.PriceListImportPhase;
import com.rodrilang.librarymanager.importer.price.model.PriceListImportJobStatus;

import java.time.Instant;
import java.time.LocalDate;

public record PriceListImportHistoryItemResponse(

        Long id,

        Long providerId,

        String providerName,

        String originalFileName,

        PriceListImportJobStatus status,

        PriceListImportPhase phase,

        LocalDate validFrom,

        Instant createdAt,

        Instant startedAt,

        Instant finishedAt,

        int totalRows,

        int processedRows,

        int processedBooks,

        int processedPrices,

        int createdBooks,

        int createdPrices,

        int updatedPrices,

        int unchangedPrices,

        int skippedRows,

        int errorCount,

        Long totalDurationMs,

        int firstPrices,

        int increasedPrices,

        int decreasedPrices,

        int maintainedPrices
) {
}