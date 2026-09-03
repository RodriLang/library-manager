package com.rodrilang.librarymanager.integrations.tiendanube.job.dto;

import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobSource;
import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobType;

public record TiendanubeJobEnqueueCommand(
        Long bookstoreId,
        Long storeId,
        Long inventoryId,
        TiendanubeJobType type,
        TiendanubeJobSource source,
        Integer maxAttempts
) {

    public TiendanubeJobEnqueueCommand(Long bookstoreId, Long storeId, Long inventoryId, TiendanubeJobType type,
                                       TiendanubeJobSource source) {
        this(bookstoreId, storeId, inventoryId, type, source, null);
    }
}
