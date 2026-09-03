package com.rodrilang.librarymanager.integrations.tiendanube.service.impl;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.integrations.tiendanube.client.TiendanubeClient;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeInventoryStatusResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeProductLinkResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubePublishResultResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeRemoteProductResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeRetryResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeProductLink;
import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeStore;
import com.rodrilang.librarymanager.integrations.tiendanube.enums.TiendanubeInventoryStatus;
import com.rodrilang.librarymanager.integrations.tiendanube.job.service.TiendanubeJobRequestService;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeProductLinkRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeStoreRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.service.TiendanubeProductLinkService;
import com.rodrilang.librarymanager.integrations.tiendanube.service.TiendanubeProductMatchingService;
import com.rodrilang.librarymanager.integrations.tiendanube.service.TiendanubeProductService;
import com.rodrilang.librarymanager.model.Inventory;
import com.rodrilang.librarymanager.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TiendanubeProductServiceImpl implements TiendanubeProductService {

    private final InventoryRepository inventoryRepository;
    private final TiendanubeStoreRepository storeRepository;
    private final TiendanubeProductLinkRepository productLinkRepository;
    private final TiendanubeClient client;
    private final TiendanubeProductLinkService productLinkService;
    private final TiendanubeProductMatchingService matchingService;
    private final TiendanubeJobRequestService jobRequestService;
    private final BookstoreContext bookstoreContext;

    @Override
    public TiendanubePublishResultResponse publishInventory(Long inventoryId) {
        jobRequestService.enqueueManualPublish(inventoryId);
        return new TiendanubePublishResultResponse(inventoryId, null, null, TiendanubeInventoryStatus.PENDING_PUBLICATION);
    }

    @Override
    public List<TiendanubeRemoteProductResponse> getRemoteProducts() {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        TiendanubeStore store = getActiveStore(bookstoreId);

        return client.getProducts(store.getStoreId()).stream()
                .map(product -> matchingService.analyze(bookstoreId, store.getStoreId(), product))
                .toList();
    }

    @Override
    public TiendanubeProductLinkResponse linkExistingProduct(Long inventoryId, Long productId, Long variantId) {
        return productLinkService.linkExistingProduct(inventoryId, productId, variantId);
    }

    @Override
    public TiendanubeRetryResponse retry(Long inventoryId) {
        return jobRequestService.enqueueManualRetry(inventoryId);
    }

    @Override
    @Transactional(readOnly = true)
    public TiendanubeInventoryStatusResponse getInventoryStatus(Long inventoryId) {
        Inventory inventory = getInventory(inventoryId);
        Long bookstoreId = inventory.getBookstore().getId();

        log.info("Estado Tiendanube inventario. inventoryId={}, status={}", inventoryId, inventory.getTiendanubeStatus());

        boolean connected = storeRepository.findByBookstoreIdAndActiveTrue(bookstoreId)
                .filter(TiendanubeStore::isTokenValid)
                .isPresent();

        if (!connected) {
            return new TiendanubeInventoryStatusResponse(
                    inventoryId, TiendanubeInventoryStatus.NOT_CONNECTED, null, null, null, null, null
            );
        }

        return productLinkRepository.findByInventoryIdAndActiveTrue(inventoryId)
                .map(link -> new TiendanubeInventoryStatusResponse(
                        inventoryId,
                        inventory.getTiendanubeStatus(),
                        link.getTiendanubeProductId(),
                        link.getTiendanubeVariantId(),
                        link.getSku(),
                        link.getLastSyncedAt(),
                        link.getLastError()
                ))
                .orElseGet(() -> new TiendanubeInventoryStatusResponse(
                        inventoryId, resolveUnlinkedStatus(inventory), null, null, null, null, null
                ));
    }

    @Override
    @Transactional
    public void unlinkInventory(Long inventoryId) {
        Inventory inventory = getInventory(inventoryId);
        TiendanubeProductLink link = productLinkRepository.findByInventoryIdAndActiveTrue(inventoryId)
                .orElseThrow(() -> new BusinessException(
                        "El inventario no tiene una publicación de Tiendanube vinculada."
                ));

        link.setActive(false);
        link.setLastError(null);
        inventory.setTiendanubeStatus(TiendanubeInventoryStatus.NOT_PUBLISHED);
        inventory.setTiendanubePriceSyncEnabled(false);

        log.info("Inventario desvinculado de Tiendanube. inventoryId={} productId={} variantId={}",
                inventoryId, link.getTiendanubeProductId(), link.getTiendanubeVariantId());
    }

    @Override
    public void deletePublication(Long inventoryId) {
        jobRequestService.enqueueManualDelete(inventoryId);
    }

    private Inventory getInventory(Long inventoryId) {
        return inventoryRepository.findById(inventoryId)
                .orElseThrow(() -> new BusinessException("No existe el inventario con id " + inventoryId));
    }

    private TiendanubeStore getActiveStore(Long bookstoreId) {
        TiendanubeStore store = storeRepository.findByBookstoreIdAndActiveTrue(bookstoreId)
                .orElseThrow(() -> new BusinessException("La librería no tiene una cuenta Tiendanube vinculada"));

        if (!store.isTokenValid()) {
            throw new BusinessException("La conexión con Tiendanube necesita volver a autorizarse");
        }

        return store;
    }

    private TiendanubeInventoryStatus resolveUnlinkedStatus(Inventory inventory) {
        TiendanubeInventoryStatus status = inventory.getTiendanubeStatus();

        if (status == TiendanubeInventoryStatus.PENDING_PUBLICATION
                || status == TiendanubeInventoryStatus.PUBLISHING
                || status == TiendanubeInventoryStatus.LINK_REQUIRED
                || status == TiendanubeInventoryStatus.SYNC_ERROR
                || status == TiendanubeInventoryStatus.REMOTE_PRODUCT_NOT_FOUND) {
            return status;
        }

        return TiendanubeInventoryStatus.NOT_PUBLISHED;
    }
}
