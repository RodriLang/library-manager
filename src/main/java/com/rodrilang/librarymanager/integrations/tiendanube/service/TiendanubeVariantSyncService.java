package com.rodrilang.librarymanager.integrations.tiendanube.service;

public interface TiendanubeVariantSyncService {

    void syncStock(Long inventoryId);

    void syncPrice(Long inventoryId);

    void syncVariant(Long inventoryId);

    void retrySync(Long inventoryId);
}