package com.rodrilang.librarymanager.importer.price.dto.internal;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record PriceListMetadata(

        String subtitle,

        String description,

        String genreName,

        Integer pageCount,

        Integer publicationYear,

        Integer publicationMonth,

        String language,

        String sourceCoverUrl,

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