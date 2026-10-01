package com.rodrilang.librarymanager.inventory.pricing.model;

public enum InventoryPriceImportClassification {
    NEW_PRICE,
    INCREASE,
    DECREASE,
    UNCHANGED,
    EXISTING_SCHEDULED_CONFLICT,
    DUPLICATE_CONFLICT,
    LARGE_CHANGE,
    INVALID_PRICE,
    AMBIGUOUS_MATCH
}
