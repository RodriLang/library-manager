package com.rodrilang.librarymanager.inventory.count.repository;

public interface InventoryCountItemSummaryProjection {

    Long getTotalItems();

    Long getTotalUnits();

    Long getResolvedItems();

    Long getPendingCatalogItems();

    Long getPendingPriceItems();

    Long getInvalidItems();

    Long getSupersededItems();

    Long getAppliedItems();
}
