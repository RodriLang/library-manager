package com.rodrilang.librarymanager.importer.price.dto.internal;

public record StagingInsertResult(
        int insertedRows,
        int duplicatedRows
) {
}