package com.rodrilang.librarymanager.cover.exception;

import com.rodrilang.librarymanager.exception.BusinessException;

public class BookCoverDoesNotBelongToBookException extends BusinessException {

    public BookCoverDoesNotBelongToBookException(
            Long coverId,
            Long bookId
    ) {
        super(
                "La portada %d no pertenece al libro %d"
                        .formatted(coverId, bookId)
        );
    }
}