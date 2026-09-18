package com.rodrilang.librarymanager.repository.criteria;

import com.rodrilang.librarymanager.dto.internal.InventoryAdvancedFilters;
import com.rodrilang.librarymanager.enums.InventoryStockFilter;

public record InventorySearchCriteria(

        String query,

        boolean force,

        InventoryStockFilter stock,

        InventoryAdvancedFilters filters

) {

    public String normalizedQuery() {
        if (query == null) {
            return "";
        }

        return query.trim();
    }

    public InventoryStockFilter resolvedStock() {
        return stock != null
                ? stock
                : InventoryStockFilter.ALL;
    }

    public InventoryAdvancedFilters resolvedFilters() {
        return filters != null
                ? filters
                : InventoryAdvancedFilters.empty();
    }
}