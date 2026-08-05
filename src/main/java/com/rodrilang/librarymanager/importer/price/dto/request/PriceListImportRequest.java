package com.rodrilang.librarymanager.importer.price.dto.request;

import jakarta.validation.constraints.NotBlank;

public record PriceListImportRequest(

        @NotBlank
        String sourceName

) {
}