package com.rodrilang.librarymanager.editorialprice.enums;

public enum EditorialPriceOrigin {
    PRICE_LIST,
    MANUAL_DISTRIBUTOR,
    MANUAL_PUBLISHER,
    MANUAL_EXTERNAL;

    public boolean isOfficial() {
        return this != MANUAL_EXTERNAL;
    }
}