package com.rodrilang.librarymanager.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

public record UpdateBookRequest(

        String title,

        String subtitle,

        String description,

        String language,

        Integer pageCount,

        LocalDate publicationDate,

        String coverUrl,

        BigDecimal retailPrice,

        LocalDate retailPriceUpdatedAt,

        Long publisherId,

        Set<Long> authorIds

) {
}