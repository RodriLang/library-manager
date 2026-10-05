package com.rodrilang.librarymanager.catalog.contribution.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.Set;

public record BookContributionRequest(
        String title,
        String subtitle,
        String description,
        String language,
        @Positive Integer pageCount,
        @Min(1000) Integer publicationYear,
        @Min(1) @Max(12) Integer publicationMonth,
        String coverUrl,
        String categoryName,
        String genreName,
        Long publisherId,
        Set<Long> authorIds,
        @Positive BigDecimal weightGrams,
        @Positive BigDecimal widthCm,
        @Positive BigDecimal heightCm,
        @Positive BigDecimal depthCm
) {
}
