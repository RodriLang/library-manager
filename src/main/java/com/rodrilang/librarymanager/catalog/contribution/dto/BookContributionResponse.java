package com.rodrilang.librarymanager.catalog.contribution.dto;

import com.rodrilang.librarymanager.dto.response.BookDetailResponse;

import java.util.List;

public record BookContributionResponse(
        BookDetailResponse book,
        List<ContributionFieldResult> results
) {
}
