package com.rodrilang.librarymanager.metadata.openlibrary.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record OpenLibraryBookResponse(

        String title,

        String subtitle,

        String description,

        @JsonProperty("number_of_pages")
        Integer numberOfPages,

        List<OpenLibraryAuthorDto> authors,

        List<OpenLibraryPublisherDto> publishers,

        OpenLibraryCoverDto cover
) {
}