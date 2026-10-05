package com.rodrilang.librarymanager.catalog.contribution.dto;

import com.rodrilang.librarymanager.catalog.contribution.enums.BookField;
import com.rodrilang.librarymanager.catalog.contribution.enums.BookFieldProposalStatus;

import java.time.Instant;

public record BookFieldProposalResponse(
        Long id,
        Long bookId,
        String bookTitle,
        String isbn,
        BookField field,
        String currentValue,
        String proposedValue,
        String currentDisplayValue,
        String proposedDisplayValue,
        Long submittedByBookstoreId,
        Long submittedByUserId,
        BookFieldProposalStatus status,
        Instant createdAt,
        Instant reviewedAt,
        Long reviewedByUserId
) {
}
