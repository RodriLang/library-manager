package com.rodrilang.librarymanager.cover.job.request;

import com.rodrilang.librarymanager.cover.enums.BookCoverSource;

public record CreateBookCoverJobRequest(
        Long bookId,
        Long priceListImportJobId,
        String sourceUrl,
        BookCoverSource source,
        Integer sourceRowNumber
) {
}