package com.rodrilang.librarymanager.importer.price.staging;

import com.rodrilang.librarymanager.importer.price.dto.internal.PriceListStagingStatistics;

final class MutableStagingStatistics {

    private long parsedRows;
    private long processableRows;
    private long validRows;
    private long invalidRows;
    private long duplicateRows;

    private long rowsWithPrice;
    private long rowsWithTitle;
    private long rowsWithIsbn;
    private long rowsWithValidIsbn;
    private long rowsWithAbsurdPrice;

    void incrementParsed() {
        parsedRows++;
    }

    void incrementProcessable() {
        processableRows++;
    }

    void addValid(long amount) {
        validRows += amount;
    }

    void addInvalid(long amount) {
        invalidRows += amount;
    }

    void addDuplicates(long amount) {
        duplicateRows += amount;
    }

    void incrementRowsWithPrice() {
        rowsWithPrice++;
    }

    void incrementRowsWithTitle() {
        rowsWithTitle++;
    }

    void incrementRowsWithIsbn() {
        rowsWithIsbn++;
    }

    void incrementRowsWithValidIsbn() {
        rowsWithValidIsbn++;
    }

    void incrementRowsWithAbsurdPrice() {
        rowsWithAbsurdPrice++;
    }

    PriceListStagingStatistics toImmutable() {
        return new PriceListStagingStatistics(
                parsedRows,
                processableRows,
                validRows,
                invalidRows,
                duplicateRows,
                rowsWithPrice,
                rowsWithTitle,
                rowsWithIsbn,
                rowsWithValidIsbn,
                rowsWithAbsurdPrice
        );
    }
}