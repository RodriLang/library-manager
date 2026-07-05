package com.rodrilang.librarymanager.dto.response;

import java.math.BigDecimal;
import java.util.Set;

public record BookSummaryResponse(

        Long id,

        String isbn,

        String title,

        String coverUrl,

        String publisherName,

        BigDecimal editorialPrice,

        Set<AuthorResponse> authors
) {
}