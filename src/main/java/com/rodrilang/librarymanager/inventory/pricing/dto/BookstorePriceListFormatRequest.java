package com.rodrilang.librarymanager.inventory.pricing.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BookstorePriceListFormatRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull @Min(0) Integer sheetIndex,
        @NotNull @Min(0) Integer firstDataRowIndex,
        @Min(0) Integer isbnColumn,
        @Min(0) Integer titleColumn,
        @Min(0) Integer authorColumn,
        @Min(0) Integer publisherColumn,
        @NotNull @Min(0) Integer priceColumn
) {
}
