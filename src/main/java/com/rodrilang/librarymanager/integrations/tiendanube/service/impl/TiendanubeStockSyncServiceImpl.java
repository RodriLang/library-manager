package com.rodrilang.librarymanager.integrations.tiendanube.service.impl;

import com.rodrilang.librarymanager.integrations.tiendanube.client.TiendanubeClient;
import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeProductLink;
import com.rodrilang.librarymanager.integrations.tiendanube.enums.TiendanubeInventoryStatus;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeProductLinkRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.service.TiendanubeInventoryStateService;
import com.rodrilang.librarymanager.integrations.tiendanube.service.TiendanubeStockSyncService;
import com.rodrilang.librarymanager.model.Inventory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class TiendanubeStockSyncServiceImpl
        implements TiendanubeStockSyncService {

    private final TiendanubeProductLinkRepository productLinkRepository;
    private final TiendanubeClient client;
    private final TiendanubeInventoryStateService inventoryStateService;

    @Override
    @Transactional
    public void syncStockByInventoryId(
            Long inventoryId,
            Integer currentStock
    ) {
        productLinkRepository
                .findByInventoryIdAndActiveTrue(inventoryId)
                .ifPresentOrElse(
                        link -> syncStock(link, currentStock),
                        () -> log.info("Inventario sin vínculo activo con Tiendanube. inventoryId={}", inventoryId)
                );
    }

    private void syncStock(
            TiendanubeProductLink link,
            Integer currentStock
    ) {
        Inventory inventory = link.getInventory();

        if (inventory.getTiendanubeStatus() != TiendanubeInventoryStatus.LINKED) {

            log.info(
                    "Sincronización omitida por estado. inventoryId={}, status={}",
                    inventory.getId(),
                    inventory.getTiendanubeStatus()
            );

            return;
        }

        try {
            client.updateStock(
                    link.getTiendanubeStoreId(),
                    link.getTiendanubeProductId(),
                    link.getTiendanubeVariantId(),
                    currentStock
            );

            link.setLastSyncedAt(Instant.now());
            link.setLastError(null);

            log.info(
                    "Stock sincronizado con Tiendanube. inventoryId={}, productId={}, variantId={}, stock={}",
                    inventory.getId(),
                    link.getTiendanubeProductId(),
                    link.getTiendanubeVariantId(),
                    currentStock
            );

        } catch (RuntimeException exception) {

            inventoryStateService.markSyncError(inventory.getId());

            throw exception;
        }
    }
}