package com.rodrilang.librarymanager.importer.price.configuration.dto.response;

import com.rodrilang.librarymanager.importer.price.configuration.enums.PriceListField;
import com.rodrilang.librarymanager.importer.price.configuration.enums.PriceListValueType;

public record PriceListColumnMappingResponse(

        Long id,
        PriceListField targetField,
        Integer columnIndex,
        String expectedHeader,
        PriceListValueType valueType,
        boolean required,
        boolean active

) {
}