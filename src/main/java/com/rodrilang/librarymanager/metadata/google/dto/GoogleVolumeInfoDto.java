package com.rodrilang.librarymanager.metadata.google.dto;

import java.util.List;

public record GoogleVolumeInfoDto(

        String title,

        String subtitle,

        List<String> authors,

        String publisher,

        String publishedDate,

        String description,

        Integer pageCount,

        String language,

        GoogleImageLinksDto imageLinks
) {
}