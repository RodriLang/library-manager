package com.rodrilang.librarymanager.dto.request;

import java.time.LocalDate;

public record UpdateBookRequest(

        String title,

        String subtitle,

        String description,

        String language,

        Integer pageCount,

        LocalDate publicationDate,

        String coverUrl

) {
}