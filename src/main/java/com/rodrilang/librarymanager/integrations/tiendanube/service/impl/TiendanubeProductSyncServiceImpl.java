package com.rodrilang.librarymanager.integrations.tiendanube.service.impl;

import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobType;
import com.rodrilang.librarymanager.integrations.tiendanube.job.service.TiendanubeJobRequestService;
import com.rodrilang.librarymanager.integrations.tiendanube.service.TiendanubeProductSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TiendanubeProductSyncServiceImpl implements TiendanubeProductSyncService {

    private final TiendanubeJobRequestService jobRequestService;

    @Override
    public void syncMissingImage(Long inventoryId) {
        jobRequestService.enqueueAutomaticLinked(inventoryId, TiendanubeJobType.SYNC_PUBLICATION);
    }

    @Override
    public void syncAfterImport(Long inventoryId) {
        jobRequestService.enqueueAutomaticLinked(inventoryId, TiendanubeJobType.SYNC_PUBLICATION);
    }

    @Override
    public void syncPublication(Long inventoryId) {
        jobRequestService.enqueueManualLinked(inventoryId, TiendanubeJobType.SYNC_PUBLICATION);
    }
}
