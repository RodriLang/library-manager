package com.rodrilang.librarymanager.importer.price.configuration.dto;

import com.rodrilang.librarymanager.importer.price.configuration.enums.HeaderStrategy;
import com.rodrilang.librarymanager.importer.price.configuration.enums.SheetStrategy;

import java.util.List;

public record PriceListImportConfigResponse(

        Long id,

        Long providerId,

        String name,

        SheetStrategy sheetStrategy,

        Integer sheetIndex,

        String sheetName,

        HeaderStrategy headerStrategy,

        Integer headerRowIndex,

        Integer firstDataRowIndex,

        boolean active,

        List<PriceListColumnMappingResponse> mappings

) {
}