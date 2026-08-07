package com.rodrilang.librarymanager.cover.exception;

import com.rodrilang.librarymanager.exception.ResourceNotFoundException;

public class BookCoverNotFoundException extends ResourceNotFoundException {

    public BookCoverNotFoundException(Long coverId) {
        super("No se encontró la portada con id " + coverId);
    }

    public static BookCoverNotFoundException primaryForBook(
            Long bookId
    ) {
        return new BookCoverNotFoundException(
                "No se encontró una portada principal para el libro " + bookId
        );
    }

    private BookCoverNotFoundException(String message) {
        super(message);
    }
}