package com.rodrilang.librarymanager.inventory.pricing.dto;

public record BookstorePriceListFormatResponse(
        Long id,
        String name,
        boolean standard,
        Integer sheetIndex,
        Integer firstDataRowIndex,
        Integer isbnColumn,
        Integer titleColumn,
        Integer authorColumn,
        Integer publisherColumn,
        Integer priceColumn,
        boolean active
) {
}
