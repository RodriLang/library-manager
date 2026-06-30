package com.rodrilang.librarymanager.dto.response;

public record BookSummaryResponse(

        Long id,

        String isbn,

        String title,

        String coverUrl
) {
}