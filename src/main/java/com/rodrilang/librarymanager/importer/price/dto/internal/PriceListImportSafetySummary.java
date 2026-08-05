package com.rodrilang.librarymanager.importer.price.dto.internal;

public record PriceListImportSafetySummary(
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

    public long effectiveRows() {
        return Math.max(0, processableRows - duplicateRows);
    }
}