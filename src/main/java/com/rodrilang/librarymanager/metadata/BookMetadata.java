package com.rodrilang.librarymanager.metadata;

import java.util.Set;

public record BookMetadata(

        String isbn,

        String title,

        String subtitle,

        String description,

        Integer pageCount,

        String language,

        String publisher,

        Set<String> authors,

        Integer publicationYear,

        Integer publicationMonth,

        String coverUrl
) {
}