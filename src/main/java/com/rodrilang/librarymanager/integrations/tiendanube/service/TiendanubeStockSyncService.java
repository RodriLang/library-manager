package com.rodrilang.librarymanager.integrations.tiendanube.service;

public interface TiendanubeStockSyncService {

    void syncStockByBookId(Long bookId, Integer currentStock);

}
