package com.rodrilang.librarymanager.dto.request;

import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

public record UpdateBookRequest(

        String title,

        String subtitle,

        String description,

        String language,

        LocalDate publicationDate,

        String coverUrl,

        String categoryName,

        String genreName,

        Long publisherId,

        Set<Long> authorIds,

        @Positive
        Integer pageCount,

        @Positive
        BigDecimal weightGrams,

        @Positive
        BigDecimal widthCm,

        @Positive
        BigDecimal heightCm,

        @Positive
        BigDecimal depthCm

) {
}