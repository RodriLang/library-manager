package com.rodrilang.librarymanager.integrations.tiendanube.job.dto;

import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobSource;
import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobType;

import java.util.UUID;

public record TiendanubeJobExecutionContext(
        Long jobId,
        Long attemptId,
        int attemptNumber,
        int maxAttempts,
        Long bookstoreId,
        Long tiendanubeStoreId,
        Long storeId,
        Long inventoryId,
        TiendanubeJobType type,
        TiendanubeJobSource source,
        UUID processingToken
) {
}
