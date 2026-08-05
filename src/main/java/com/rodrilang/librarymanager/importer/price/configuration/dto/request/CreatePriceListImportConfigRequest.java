package com.rodrilang.librarymanager.importer.price.configuration.dto.request;

import com.rodrilang.librarymanager.importer.price.configuration.enums.HeaderStrategy;
import com.rodrilang.librarymanager.importer.price.configuration.enums.SheetStrategy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreatePriceListImportConfigRequest(

        @NotBlank
        @Size(max = 100)
        String name,

        @NotNull
        SheetStrategy sheetStrategy,

        @Min(0)
        Integer sheetIndex,

        @Size(max = 200)
        String sheetName,

        @NotNull
        HeaderStrategy headerStrategy,

        @Min(0)
        Integer headerRowIndex,

        @NotNull
        @Min(0)
        Integer firstDataRowIndex,

        @NotEmpty
        List<@Valid PriceListColumnMappingRequest> mappings

) {
}