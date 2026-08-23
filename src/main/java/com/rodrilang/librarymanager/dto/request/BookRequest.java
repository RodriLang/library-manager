package com.rodrilang.librarymanager.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.Set;

public record BookRequest(

        String isbn,

        String title,

        String subtitle,

        String description,

        String language,

        @Min(1000)
        Integer publicationYear,

        @Min(1)
        @Max(12)
        Integer publicationMonth,

        String coverUrl,

        String categoryName,

        String genreName,

        @Positive
        Integer pageCount,

        @Positive
        BigDecimal weightGrams,

        @Positive
        BigDecimal widthCm,

        @Positive
        BigDecimal heightCm,

        @Positive
        BigDecimal depthCm,

        Long publisherId,

        Set<Long> authorIds

) {
}