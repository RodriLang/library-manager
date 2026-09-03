package com.rodrilang.librarymanager.integrations.tiendanube.service.impl;

import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobType;
import com.rodrilang.librarymanager.integrations.tiendanube.job.service.TiendanubeJobRequestService;
import com.rodrilang.librarymanager.integrations.tiendanube.service.TiendanubeStockSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TiendanubeStockSyncServiceImpl implements TiendanubeStockSyncService {

    private final TiendanubeJobRequestService jobRequestService;

    @Override
    public void syncStockByInventoryId(Long inventoryId, Integer currentStock) {
        jobRequestService.enqueueAutomaticLinked(inventoryId, TiendanubeJobType.SYNC_STOCK);
    }
}
