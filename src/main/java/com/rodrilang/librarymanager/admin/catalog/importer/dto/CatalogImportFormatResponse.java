package com.rodrilang.librarymanager.admin.catalog.importer.dto;

import com.rodrilang.librarymanager.importer.price.configuration.enums.*;
import java.util.List;

public record CatalogImportFormatResponse(
        Long id, Long providerId, String providerName, String name,
        SheetStrategy sheetStrategy, Integer sheetIndex, String sheetName,
        HeaderStrategy headerStrategy, Integer headerRowIndex, Integer firstDataRowIndex,
        boolean active, List<MappingResponse> mappings
) {
    public record MappingResponse(PriceListField targetField, Integer columnIndex, String expectedHeader,
                                  PriceListValueType valueType, boolean required, boolean active) {}
}
