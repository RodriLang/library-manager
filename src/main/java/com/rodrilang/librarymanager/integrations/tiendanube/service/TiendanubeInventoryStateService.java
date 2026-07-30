package com.rodrilang.librarymanager.integrations.tiendanube.service;

import com.rodrilang.librarymanager.integrations.tiendanube.enums.TiendanubeInventoryStatus;

public interface TiendanubeInventoryStateService {

    void updateStatus(Long inventoryId, TiendanubeInventoryStatus status);

    void markSyncError(Long inventoryId);

    void markSyncError(Long inventoryId, Long linkId, String error);
}