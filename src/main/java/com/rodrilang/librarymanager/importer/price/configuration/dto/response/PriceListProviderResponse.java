package com.rodrilang.librarymanager.importer.price.configuration.dto.response;

public record PriceListProviderResponse(

        Long id,
        String code,
        String name,
        boolean active

) {
}