package com.rodrilang.librarymanager.integrations.tiendanube.service.impl;

import com.rodrilang.librarymanager.integrations.tiendanube.client.TiendanubeClient;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.request.TiendanubeUpdateVariantRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeProductLink;
import com.rodrilang.librarymanager.integrations.tiendanube.enums.TiendanubeInventoryStatus;
import com.rodrilang.librarymanager.integrations.tiendanube.exception.TiendanubeRemoteResourceNotFoundException;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeProductLinkRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.service.TiendanubeInventoryStateService;
import com.rodrilang.librarymanager.integrations.tiendanube.service.TiendanubeVariantSyncService;
import com.rodrilang.librarymanager.integrations.tiendanube.util.TiendanubeProductUtils;
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

    private static final String NO_LINK_INVENTORY_MESSAGE =
            "Inventario sin vínculo activo con Tiendanube. inventoryId={}";

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

    @Override
    @Transactional
    public void retrySync(Long inventoryId) {
        TiendanubeProductLink link = productLinkRepository.findByInventoryIdAndActiveTrue(inventoryId)
                .orElseThrow(() -> new IllegalStateException(
                        "El inventario no tiene un vínculo activo con Tiendanube"
                ));

        Inventory inventory = link.getInventory();

        if (inventory.getTiendanubeStatus() != TiendanubeInventoryStatus.SYNC_ERROR) {
            throw new IllegalStateException(
                    "El inventario no se encuentra en estado SYNC_ERROR"
            );
        }

        syncVariantInternal(link, true);
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

            log.info("Stock sincronizado con Tiendanube. inventoryId={}, stock={}",
                    inventory.getId(), currentStock);

        } catch (RuntimeException exception) {
            handleFailure(link, exception);
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
            handleFailure(link, exception);
            throw exception;
        }
    }

    private void syncVariant(TiendanubeProductLink link) {
        syncVariantInternal(link, false);
    }

    private void syncVariantInternal(TiendanubeProductLink link, boolean allowSyncError) {
        Inventory inventory = link.getInventory();

        if (!canSync(inventory, allowSyncError)) {
            return;
        }

        try {
            String isbn = TiendanubeProductUtils.normalizeIdentifier(inventory.getBook().getPreferredIsbn());
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
            inventory.setTiendanubeStatus(TiendanubeInventoryStatus.LINKED);

            log.info("Variante sincronizada con Tiendanube. inventoryId={}, price={}, stock={}",
                    inventory.getId(), inventory.getSalePrice(), inventory.getStock());

        } catch (RuntimeException exception) {
            handleFailure(link, exception);
            throw exception;
        }
    }

    private boolean canSync(Inventory inventory) {
        return canSync(inventory, false);
    }

    private boolean canSync(Inventory inventory, boolean allowSyncError) {
        TiendanubeInventoryStatus status = inventory.getTiendanubeStatus();

        if (status == TiendanubeInventoryStatus.LINKED) {
            return true;
        }

        if (allowSyncError && status == TiendanubeInventoryStatus.SYNC_ERROR) {
            return true;
        }

        log.info("Sincronización omitida por estado. inventoryId={}, status={}",
                inventory.getId(), status);

        return false;
    }

    private void registerSuccess(TiendanubeProductLink link) {
        link.setLastSyncedAt(Instant.now());
        link.setLastError(null);
    }

    private void handleFailure(TiendanubeProductLink link, RuntimeException exception) {
        if (exception instanceof TiendanubeRemoteResourceNotFoundException) {
            inventoryStateService.updateStatus(
                    link.getInventory().getId(),
                    TiendanubeInventoryStatus.REMOTE_PRODUCT_NOT_FOUND
            );

            log.warn("Producto remoto no encontrado en Tiendanube. inventoryId={}, productId={}, variantId={}",
                    link.getInventory().getId(),
                    link.getTiendanubeProductId(),
                    link.getTiendanubeVariantId());

            return;
        }

        registerFailure(link, exception);
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
}