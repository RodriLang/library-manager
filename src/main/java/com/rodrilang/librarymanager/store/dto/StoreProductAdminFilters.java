package com.rodrilang.librarymanager.store.dto;

import com.rodrilang.librarymanager.enums.BookCondition;
import com.rodrilang.librarymanager.enums.InventoryPriceMode;
import com.rodrilang.librarymanager.enums.InventoryStockFilter;

import java.util.List;

public record StoreProductAdminFilters(
        String q,
        Boolean published,
        Boolean featured,
        InventoryStockFilter stock,
        BookCondition condition,
        List<Long> publisherIds,
        List<Long> authorIds,
        InventoryPriceMode priceMode,
        Boolean consignment
) {
    public StoreProductAdminFilters {
        stock = stock != null ? stock : InventoryStockFilter.ALL;
        publisherIds = publisherIds != null ? List.copyOf(publisherIds) : List.of();
        authorIds = authorIds != null ? List.copyOf(authorIds) : List.of();
        priceMode = priceMode != null ? priceMode : InventoryPriceMode.ALL;
    }

    public String normalizedQuery() {
        return q == null ? "" : q.trim().toLowerCase();
    }
}
