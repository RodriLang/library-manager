package com.rodrilang.librarymanager.dto.request;

import java.time.LocalDate;
import java.util.Set;

public record BookRequest(

        String isbn,

        String title,

        String subtitle,

        String description,

        String language,

        Integer pageCount,

        LocalDate publicationDate,

        String coverUrl,

        Long publisherId,

        Set<Long> authorIds
) {
}
