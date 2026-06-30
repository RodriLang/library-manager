package com.rodrilang.librarymanager.metadata.openlibrary.dto;

import java.util.Map;

public record OpenLibraryBooksApiResponse(

        Map<String, OpenLibraryBookResponse> books
) {
}