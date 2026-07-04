package com.rodrilang.librarymanager.dto.internal;

import com.rodrilang.librarymanager.model.Book;

public record BookImportResult(

        Book book,

        boolean created
) {
}