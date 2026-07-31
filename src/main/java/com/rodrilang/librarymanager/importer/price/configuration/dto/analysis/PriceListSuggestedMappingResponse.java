package com.rodrilang.librarymanager.importer.price.configuration.dto.analysis;

import com.rodrilang.librarymanager.importer.price.configuration.enums.PriceListField;
import com.rodrilang.librarymanager.importer.price.configuration.enums.PriceListValueType;

public record PriceListSuggestedMappingResponse(

        Integer columnIndex,
        String header,
        PriceListField suggestedField,
        PriceListValueType suggestedValueType

) {
}