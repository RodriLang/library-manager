package com.rodrilang.librarymanager.integrations.tiendanube.management.enums;

import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobType;

public enum TiendanubeBulkAction {
    PUBLISH(TiendanubeJobType.PUBLISH),
    SYNC_PUBLICATION(TiendanubeJobType.SYNC_PUBLICATION),
    SYNC_STOCK(TiendanubeJobType.SYNC_STOCK),
    SYNC_PRICE(TiendanubeJobType.SYNC_PRICE),
    DELETE_PUBLICATION(TiendanubeJobType.DELETE_PUBLICATION),
    UNLINK(null),
    ENABLE_PRICE_SYNC(TiendanubeJobType.SYNC_PRICE),
    DISABLE_PRICE_SYNC(null);

    private final TiendanubeJobType jobType;

    TiendanubeBulkAction(TiendanubeJobType jobType) {
        this.jobType = jobType;
    }

    public TiendanubeJobType jobType() {
        return jobType;
    }

    public boolean isLocalOnly() {
        return this == UNLINK || this == DISABLE_PRICE_SYNC;
    }

    public boolean requiresActiveLink() {
        return this != PUBLISH && this != DISABLE_PRICE_SYNC;
    }
}
