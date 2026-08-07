package com.rodrilang.librarymanager.cover.exception;

import com.rodrilang.librarymanager.exception.DuplicateResourceException;

public class DuplicateBookCoverException extends DuplicateResourceException {

    public DuplicateBookCoverException(
            Long bookId,
            String contentHash
    ) {
        super(
                "El libro %d ya tiene registrada una portada con el hash %s"
                        .formatted(bookId, contentHash)
        );
    }
}