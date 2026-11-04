package com.rodrilang.librarymanager.inventory.bulk.dto.request;

import com.rodrilang.librarymanager.enums.BookCondition;
import com.rodrilang.librarymanager.enums.InventoryActiveFilter;
import com.rodrilang.librarymanager.enums.InventoryPriceMode;
import com.rodrilang.librarymanager.enums.InventoryStockFilter;

import java.util.List;

public record InventoryBulkFilterRequest(
        String q,
        BookCondition condition,
        InventoryActiveFilter active,
        List<Long> publisherIds,
        List<Long> authorIds,
        InventoryPriceMode priceMode,
        InventoryStockFilter stock,
        Boolean consignment,
        List<Long> commercialCategoryIds,
        Boolean includeCommercialSubcategories,
        Boolean withoutCommercialCategory
) {
    public InventoryBulkFilterRequest(String q, BookCondition condition, InventoryActiveFilter active, List<Long> publisherIds, List<Long> authorIds, InventoryPriceMode priceMode, InventoryStockFilter stock) {
        this(q, condition, active, publisherIds, authorIds, priceMode, stock, null, List.of(), false, false);
    }

    public InventoryBulkFilterRequest(String q, BookCondition condition, InventoryActiveFilter active, List<Long> publisherIds, List<Long> authorIds, InventoryPriceMode priceMode, InventoryStockFilter stock, Boolean consignment) {
        this(q, condition, active, publisherIds, authorIds, priceMode, stock, consignment, List.of(), false, false);
    }

    public InventoryBulkFilterRequest {
        active = active != null ? active : InventoryActiveFilter.ACTIVE;
        publisherIds = publisherIds != null ? List.copyOf(publisherIds) : List.of();
        authorIds = authorIds != null ? List.copyOf(authorIds) : List.of();
        commercialCategoryIds = commercialCategoryIds != null ? List.copyOf(commercialCategoryIds) : List.of();
        priceMode = priceMode != null ? priceMode : InventoryPriceMode.ALL;
        stock = stock != null ? stock : InventoryStockFilter.ALL;
    }
}
