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
        InventoryActiveFilter active,
        Boolean consignment,
        List<Long> commercialCategoryIds,
        Boolean includeCommercialSubcategories,
        Boolean withoutCommercialCategory
) {

    public InventoryAdvancedFilters {
        publisherIds = publisherIds != null ? List.copyOf(publisherIds) : List.of();
        commercialCategoryIds = commercialCategoryIds != null ? List.copyOf(commercialCategoryIds) : List.of();
        authorIds = authorIds != null ? List.copyOf(authorIds) : List.of();
        priceMode = priceMode != null ? priceMode : InventoryPriceMode.ALL;
        active = active != null ? active : InventoryActiveFilter.ACTIVE;
    }

    public InventoryAdvancedFilters(BookCondition condition, List<Long> publisherIds, List<Long> authorIds, InventoryPriceMode priceMode, InventoryActiveFilter active, Boolean consignment) {
        this(condition, publisherIds, authorIds, priceMode, active, consignment, List.of(), false, false);
    }

    public InventoryAdvancedFilters(BookCondition condition, List<Long> publisherIds, List<Long> authorIds, InventoryPriceMode priceMode, InventoryActiveFilter active) {
        this(condition, publisherIds, authorIds, priceMode, active, null);
    }

    public static InventoryAdvancedFilters empty() {
        return new InventoryAdvancedFilters(
                null,
                List.of(),
                List.of(),
                InventoryPriceMode.ALL,
                InventoryActiveFilter.ACTIVE,
                null
        );
    }

    public InventoryPriceMode resolvedPriceMode() {
        return priceMode != null ? priceMode : InventoryPriceMode.ALL;
    }

    public InventoryActiveFilter resolvedActive() {
        return active != null ? active : InventoryActiveFilter.ACTIVE;
    }

    public boolean commercialSubcategoriesIncluded() { return Boolean.TRUE.equals(includeCommercialSubcategories); }

    public boolean unclassifiedOnly() { return Boolean.TRUE.equals(withoutCommercialCategory); }

    public boolean consignmentOnly() {
        return Boolean.TRUE.equals(consignment);
    }
}
