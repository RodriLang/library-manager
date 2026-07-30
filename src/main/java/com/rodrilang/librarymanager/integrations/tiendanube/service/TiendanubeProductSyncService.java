package com.rodrilang.librarymanager.integrations.tiendanube.service;

public interface TiendanubeProductSyncService {

    void syncMissingImage(Long inventoryId);

    void syncAfterImport(Long inventoryId);
}