package com.rodrilang.librarymanager.inventory.bulk.model;

public enum InventoryBulkAction {

    SET_MINIMUM_STOCK,
    MARK_FOR_REPLENISHMENT,

    ACTIVATE,
    DEACTIVATE,
    MARK_AS_CONSIGNMENT,
    CLEAR_CONSIGNMENT
}