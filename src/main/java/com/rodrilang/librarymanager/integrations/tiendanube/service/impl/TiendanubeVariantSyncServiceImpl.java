package com.rodrilang.librarymanager.integrations.tiendanube.service.impl;

import com.rodrilang.librarymanager.integrations.tiendanube.client.TiendanubeClient;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.request.TiendanubeUpdateVariantRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeProductLink;
import com.rodrilang.librarymanager.integrations.tiendanube.enums.TiendanubeInventoryStatus;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeProductLinkRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.service.TiendanubeInventoryStateService;
import com.rodrilang.librarymanager.integrations.tiendanube.service.TiendanubeVariantSyncService;
import com.rodrilang.librarymanager.model.Inventory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class TiendanubeVariantSyncServiceImpl implements TiendanubeVariantSyncService {

    private static final String NO_LINK_INVENTORY_MESSAGE = "Inventario sin vínculo activo con Tiendanube. inventoryId={}";

    private final TiendanubeProductLinkRepository productLinkRepository;
    private final TiendanubeClient client;
    private final TiendanubeInventoryStateService inventoryStateService;

    @Override
    @Transactional
    public void syncStock(Long inventoryId, Integer currentStock) {
        productLinkRepository.findByInventoryIdAndActiveTrue(inventoryId)
                .ifPresentOrElse(
                        link -> syncStock(link, currentStock),
                        () -> log.info(NO_LINK_INVENTORY_MESSAGE, inventoryId)
                );
    }

    @Override
    @Transactional
    public void syncPrice(Long inventoryId) {
        productLinkRepository.findByInventoryIdAndActiveTrue(inventoryId)
                .ifPresentOrElse(
                        this::syncPrice,
                        () -> log.info(NO_LINK_INVENTORY_MESSAGE, inventoryId)
                );
    }

    @Override
    @Transactional
    public void syncVariant(Long inventoryId) {
        productLinkRepository.findByInventoryIdAndActiveTrue(inventoryId)
                .ifPresentOrElse(
                        this::syncVariant,
                        () -> log.info(NO_LINK_INVENTORY_MESSAGE, inventoryId)
                );
    }

    private void syncStock(TiendanubeProductLink link, Integer currentStock) {
        Inventory inventory = link.getInventory();

        if (!canSync(inventory)) {
            return;
        }

        try {
            client.updateStock(
                    link.getTiendanubeStoreId(),
                    link.getTiendanubeProductId(),
                    link.getTiendanubeVariantId(),
                    currentStock
            );

            registerSuccess(link);

            log.info("Stock sincronizado con Tiendanube. inventoryId={}, stock={}", inventory.getId(), currentStock);

        } catch (RuntimeException exception) {
            registerFailure(link, exception);
            throw exception;
        }
    }

    private void syncPrice(TiendanubeProductLink link) {
        Inventory inventory = link.getInventory();

        if (!canSync(inventory)) {
            return;
        }

        try {
            TiendanubeUpdateVariantRequest request = new TiendanubeUpdateVariantRequest(
                    null,
                    null,
                    inventory.getSalePrice(),
                    null,
                    null
            );

            client.updateVariant(
                    link.getTiendanubeStoreId(),
                    link.getTiendanubeProductId(),
                    link.getTiendanubeVariantId(),
                    request
            );

            registerSuccess(link);

            log.info("Precio sincronizado con Tiendanube. inventoryId={}, price={}",
                    inventory.getId(), inventory.getSalePrice());

        } catch (RuntimeException exception) {
            registerFailure(link, exception);
            throw exception;
        }
    }

    private void syncVariant(TiendanubeProductLink link) {
        Inventory inventory = link.getInventory();

        if (!canSync(inventory)) {
            return;
        }

        try {
            String isbn = normalizeIdentifier(inventory.getBook().getIsbn());
            String sku = link.getSku();

            if ((sku == null || sku.isBlank()) && isbn != null) {
                sku = isbn;
            }

            TiendanubeUpdateVariantRequest request = new TiendanubeUpdateVariantRequest(
                    sku,
                    isbn,
                    inventory.getSalePrice(),
                    inventory.getStock(),
                    true
            );

            client.updateVariant(
                    link.getTiendanubeStoreId(),
                    link.getTiendanubeProductId(),
                    link.getTiendanubeVariantId(),
                    request
            );

            if (link.getSku() == null || link.getSku().isBlank()) {
                link.setSku(sku);
            }

            registerSuccess(link);

            log.info("Variante sincronizada con Tiendanube. inventoryId={}, price={}, stock={}",
                    inventory.getId(), inventory.getSalePrice(), inventory.getStock());

        } catch (RuntimeException exception) {
            registerFailure(link, exception);
            throw exception;
        }
    }

    private boolean canSync(Inventory inventory) {
        if (inventory.getTiendanubeStatus() == TiendanubeInventoryStatus.LINKED) {
            return true;
        }

        log.info("Sincronización omitida por estado. inventoryId={}, status={}",
                inventory.getId(), inventory.getTiendanubeStatus());

        return false;
    }

    private void registerSuccess(TiendanubeProductLink link) {
        link.setLastSyncedAt(Instant.now());
        link.setLastError(null);
    }

    private void registerFailure(TiendanubeProductLink link, RuntimeException exception) {
        String error = exception.getMessage() != null
                ? exception.getMessage()
                : exception.getClass().getSimpleName();

        inventoryStateService.markSyncError(
                link.getInventory().getId(),
                link.getId(),
                error
        );

        log.error("Error sincronizando variante con Tiendanube. inventoryId={}, productId={}, variantId={}",
                link.getInventory().getId(),
                link.getTiendanubeProductId(),
                link.getTiendanubeVariantId(),
                exception);
    }

    private String normalizeIdentifier(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.replace("-", "").replace(" ", "").trim();
    }
}