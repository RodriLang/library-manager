package com.rodrilang.librarymanager.importer.price.dto.internal;

public record PriceListStagingStatistics(
        long parsedRows,
        long processableRows,
        long validRows,
        long invalidRows,
        long duplicateRows,
        long rowsWithPrice,
        long rowsWithTitle,
        long rowsWithIsbn,
        long rowsWithValidIsbn,
        long rowsWithAbsurdPrice
) {
}