package com.rodrilang.librarymanager.integrations.tiendanube.service.impl;

import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobType;
import com.rodrilang.librarymanager.integrations.tiendanube.job.service.TiendanubeJobRequestService;
import com.rodrilang.librarymanager.integrations.tiendanube.service.TiendanubeVariantSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TiendanubeVariantSyncServiceImpl implements TiendanubeVariantSyncService {

    private final TiendanubeJobRequestService jobRequestService;

    @Override
    public void syncStock(Long inventoryId) {
        jobRequestService.enqueueAutomaticLinked(inventoryId, TiendanubeJobType.SYNC_STOCK);
    }

    @Override
    public void syncPrice(Long inventoryId) {
        jobRequestService.enqueueAutomaticLinked(inventoryId, TiendanubeJobType.SYNC_PRICE);
    }

    @Override
    public void syncVariant(Long inventoryId) {
        jobRequestService.enqueueAutomaticLinked(inventoryId, TiendanubeJobType.SYNC_PUBLICATION);
    }

    @Override
    public void retrySync(Long inventoryId) {
        jobRequestService.enqueueManualLinked(inventoryId, TiendanubeJobType.SYNC_PUBLICATION);
    }
}
