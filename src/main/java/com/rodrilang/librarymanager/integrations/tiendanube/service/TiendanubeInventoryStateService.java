package com.rodrilang.librarymanager.integrations.tiendanube.service;

public interface TiendanubeInventoryStateService {

    void markSyncError(Long inventoryId);

    void markSyncError(Long inventoryId, Long linkId, String error);
}