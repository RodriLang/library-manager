package com.rodrilang.librarymanager.repository.projection;

public interface InventoryStockSummaryProjection {

    long getTotal();

    long getAvailable();

    long getLowStock();

    long getOutOfStock();
}