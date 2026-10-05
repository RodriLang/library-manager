package com.rodrilang.librarymanager.admin.catalog.importer.dto;

import com.rodrilang.librarymanager.importer.price.configuration.enums.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record CatalogImportFormatRequest(
        @NotNull Long providerId,
        @NotBlank @Size(max = 150) String name,
        @NotNull SheetStrategy sheetStrategy,
        Integer sheetIndex,
        @Size(max = 150) String sheetName,
        @NotNull HeaderStrategy headerStrategy,
        Integer headerRowIndex,
        @NotNull @Min(0) Integer firstDataRowIndex,
        Boolean active,
        @NotEmpty List<@Valid MappingRequest> mappings
) {
    public record MappingRequest(
            @NotNull PriceListField targetField,
            @NotNull @Min(0) Integer columnIndex,
            @Size(max = 200) String expectedHeader,
            @NotNull PriceListValueType valueType,
            boolean required,
            boolean active
    ) {}
}
