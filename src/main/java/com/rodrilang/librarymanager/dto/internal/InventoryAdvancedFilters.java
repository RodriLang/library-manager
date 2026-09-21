package com.rodrilang.librarymanager.dto.internal;

import com.rodrilang.librarymanager.enums.BookCondition;
import com.rodrilang.librarymanager.enums.InventoryActiveFilter;
import com.rodrilang.librarymanager.enums.InventoryPriceMode;

import java.util.List;

public record InventoryAdvancedFilters(
        BookCondition condition,
        List<Long> publisherIds,
        List<Long> authorIds,
        InventoryPriceMode priceMode,
        InventoryActiveFilter active
) {

    public InventoryAdvancedFilters {
        publisherIds =
                publisherIds != null
                        ? List.copyOf(publisherIds)
                        : List.of();

        authorIds =
                authorIds != null
                        ? List.copyOf(authorIds)
                        : List.of();

        priceMode =
                priceMode != null
                        ? priceMode
                        : InventoryPriceMode.ALL;

        /*
         * Preservamos el comportamiento histórico de Inventario:
         * si no se especifica nada, muestra activos.
         */
        active =
                active != null
                        ? active
                        : InventoryActiveFilter.ACTIVE;
    }

    public static InventoryAdvancedFilters empty() {
        return new InventoryAdvancedFilters(
                null,
                List.of(),
                List.of(),
                InventoryPriceMode.ALL,
                InventoryActiveFilter.ACTIVE
        );
    }

    public InventoryPriceMode resolvedPriceMode() {
        return priceMode != null
                ? priceMode
                : InventoryPriceMode.ALL;
    }

    public InventoryActiveFilter resolvedActive() {
        return active != null
                ? active
                : InventoryActiveFilter.ACTIVE;
    }
}