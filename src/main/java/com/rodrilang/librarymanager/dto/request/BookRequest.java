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

        String categoryName,

        String genreName,

        Long publisherId,

        Set<Long> authorIds

) {
}
