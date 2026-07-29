package com.rodrilang.librarymanager.integrations.tiendanube.service;

public interface TiendanubeStockSyncService {

    void syncStockByInventoryId(Long inventoryId, Integer currentStock);
}
