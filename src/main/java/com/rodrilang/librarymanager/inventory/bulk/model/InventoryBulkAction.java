package com.rodrilang.librarymanager.inventory.bulk.model;

public enum InventoryBulkAction {

    SET_MINIMUM_STOCK,
    MARK_FOR_REPLENISHMENT,

    ACTIVATE,
    DEACTIVATE,

    ENABLE_EDITORIAL_PRICE_SYNC,
    DISABLE_EDITORIAL_PRICE_SYNC
}