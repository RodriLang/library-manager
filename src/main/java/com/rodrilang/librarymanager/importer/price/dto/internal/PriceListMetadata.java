package com.rodrilang.librarymanager.importer.price.dto.internal;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;

@Builder
public record PriceListMetadata(

        String subtitle,

        String description,

        String genreName,

        Integer pageCount,

        LocalDate publicationDate,

        String language,

        String coverUrl,

        String collectionName,

        BigDecimal widthCm,

        BigDecimal heightCm,

        BigDecimal depthCm,

        BigDecimal weightGrams,

        String tags,

        String externalCode,

        Integer externalStock,

        String observations

) {
}