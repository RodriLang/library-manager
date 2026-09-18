package com.rodrilang.librarymanager.dto.internal;

import com.rodrilang.librarymanager.enums.BookCondition;
import com.rodrilang.librarymanager.enums.InventoryPriceMode;

public record InventoryAdvancedFilters(

        BookCondition condition,

        Long publisherId,

        Long authorId,

        InventoryPriceMode priceMode

) {

    public static InventoryAdvancedFilters empty() {
        return new InventoryAdvancedFilters(
                null,
                null,
                null,
                InventoryPriceMode.ALL
        );
    }

    public InventoryPriceMode resolvedPriceMode() {
        return priceMode != null
                ? priceMode
                : InventoryPriceMode.ALL;
    }
}