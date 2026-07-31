package com.rodrilang.librarymanager.integrations.tiendanube.enums;

public enum TiendanubeImportMatchType {
    ALREADY_LINKED,
    INVENTORY_ALREADY_LINKED,
    INVENTORY_EXISTS,
    EXACT_BARCODE,
    EXACT_SKU,
    POSSIBLE_MATCH,
    MULTIPLE_MATCHES,
    BOOK_NOT_FOUND
}