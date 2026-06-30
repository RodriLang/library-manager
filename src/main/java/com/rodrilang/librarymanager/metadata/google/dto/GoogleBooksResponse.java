package com.rodrilang.librarymanager.metadata.google.dto;

import java.util.List;

public record GoogleBooksResponse(

        Integer totalItems,

        List<GoogleBookItemDto> items
) {
}