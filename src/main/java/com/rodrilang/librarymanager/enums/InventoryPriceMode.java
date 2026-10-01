package com.rodrilang.librarymanager.enums;

public enum InventoryPriceMode {
    ALL,
    WITH_PRICE,
    WITHOUT_PRICE,
    /** @deprecated legado del modelo de precio editorial */
    @Deprecated EDITORIAL,
    /** @deprecated legado del modelo de precio editorial */
    @Deprecated INDEPENDENT
}
