package com.rodrilang.librarymanager.importer.price.dto;

import jakarta.validation.constraints.NotBlank;

public record PriceListImportRequest(

        @NotBlank
        String sourceName

) {
}