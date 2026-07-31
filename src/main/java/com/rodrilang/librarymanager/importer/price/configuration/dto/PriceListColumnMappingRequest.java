package com.rodrilang.librarymanager.importer.price.configuration.dto;

import com.rodrilang.librarymanager.importer.price.configuration.enums.PriceListField;
import com.rodrilang.librarymanager.importer.price.configuration.enums.PriceListValueType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PriceListColumnMappingRequest(

        @NotNull
        PriceListField targetField,

        @NotNull
        @Min(0)
        Integer columnIndex,

        @Size(max = 150)
        String expectedHeader,

        @NotNull
        PriceListValueType valueType,

        boolean required

) {
}