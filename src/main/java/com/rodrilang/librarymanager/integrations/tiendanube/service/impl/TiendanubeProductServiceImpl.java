package com.rodrilang.librarymanager.integrations.tiendanube.service.impl;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import com.rodrilang.librarymanager.integrations.tiendanube.client.TiendanubeClient;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.internal.RemoteInventoryMatch;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.request.TiendanubeCreateProductRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeInventoryStatusResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeProductLinkResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeProductResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubePublishResultResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeRemoteProductResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeRetryResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeVariantResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeProductLink;
import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeStore;
import com.rodrilang.librarymanager.integrations.tiendanube.enums.TiendanubeInventoryStatus;
import com.rodrilang.librarymanager.integrations.tiendanube.factory.TiendanubeProductRequestFactory;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeProductLinkRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeStoreRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.service.TiendanubeInventoryStateService;
import com.rodrilang.librarymanager.integrations.tiendanube.service.TiendanubeProductLinkPersistenceService;
import com.rodrilang.librarymanager.integrations.tiendanube.service.TiendanubeProductLinkService;
import com.rodrilang.librarymanager.integrations.tiendanube.service.TiendanubeProductMatchingService;
import com.rodrilang.librarymanager.integrations.tiendanube.service.TiendanubeProductService;
import com.rodrilang.librarymanager.integrations.tiendanube.service.TiendanubeVariantSyncService;
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
    private final TiendanubeProductRequestFactory productRequestFactory;
    private final TiendanubeClient client;
    private final TiendanubeVariantSyncService variantSyncService;
    private final TiendanubeInventoryStateService inventoryStateService;
    private final TiendanubeProductLinkPersistenceService linkPersistenceService;
    private final TiendanubeProductLinkService productLinkService;
    private final TiendanubeProductMatchingService matchingService;
    private final BookstoreContext bookstoreContext;

    @Override
    @Transactional
    public TiendanubePublishResultResponse publishInventory(Long inventoryId) {
        Inventory inventory = inventoryRepository
                .findByIdForTiendanubePublish(inventoryId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "No se encontró el inventario."
                        )
                );
        TiendanubeStore store = getActiveStore(inventory.getBookstore().getId());

        validateCanPublish(inventory, store.getStoreId());

        List<TiendanubeProductResponse> remoteProducts = client.getProducts(store.getStoreId());
        RemoteInventoryMatch remoteMatch = matchingService.findRemoteMatch(inventory, remoteProducts);

        if (remoteMatch != null) {
            if (remoteMatch.autoLink()) {
                TiendanubeProductLinkResponse link = productLinkService.linkExistingProduct(
                        inventoryId,
                        remoteMatch.productId(),
                        remoteMatch.variantId()
                );

                return new TiendanubePublishResultResponse(
                        inventoryId,
                        link.productId(),
                        link.variantId(),
                        TiendanubeInventoryStatus.LINKED
                );
            }

            inventoryStateService.updateStatus(inventoryId, TiendanubeInventoryStatus.LINK_REQUIRED);

            return new TiendanubePublishResultResponse(
                    inventoryId,
                    null,
                    null,
                    TiendanubeInventoryStatus.LINK_REQUIRED
            );
        }

        inventoryStateService.updateStatus(inventoryId, TiendanubeInventoryStatus.PUBLISHING);

        try {
            TiendanubeCreateProductRequest request = productRequestFactory.createProduct(inventory);
            TiendanubeProductResponse remoteProduct = client.createProduct(store.getStoreId(), request);
            TiendanubeVariantResponse remoteVariant = getMainVariant(remoteProduct);

            linkPersistenceService.savePublishedLink(
                    inventoryId,
                    store.getStoreId(),
                    remoteProduct.id(),
                    remoteVariant
            );

            log.info("Inventario publicado en Tiendanube. inventoryId={}, productId={}, variantId={}",
                    inventoryId, remoteProduct.id(), remoteVariant.id());

            return new TiendanubePublishResultResponse(
                    inventoryId,
                    remoteProduct.id(),
                    remoteVariant.id(),
                    TiendanubeInventoryStatus.LINKED
            );

        } catch (RuntimeException exception) {
            inventoryStateService.markSyncError(inventoryId);
            log.error("Error publicando inventario en Tiendanube. inventoryId={}", inventoryId, exception);
            throw exception;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TiendanubeRemoteProductResponse> getRemoteProducts() {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();

        TiendanubeStore store = getActiveStore(bookstoreId);

        return client.getProducts(store.getStoreId()).stream()
                .map(product -> matchingService.analyze(bookstoreId, store.getStoreId(), product))
                .toList();
    }

    @Override
    public TiendanubeProductLinkResponse linkExistingProduct(
            Long inventoryId,
            Long productId,
            Long variantId
    ) {
        return productLinkService.linkExistingProduct(inventoryId, productId, variantId);
    }

    @Override
    public TiendanubeRetryResponse retry(Long inventoryId) {
        Inventory inventory = getInventory(inventoryId);

        if (inventory.getTiendanubeStatus() != TiendanubeInventoryStatus.SYNC_ERROR) {
            throw new BusinessException("El inventario no se encuentra en estado de error");
        }

        if (productLinkRepository.findByInventoryIdAndActiveTrue(inventoryId).isPresent()) {
            variantSyncService.retrySync(inventoryId);

            return new TiendanubeRetryResponse(
                    inventoryId,
                    TiendanubeInventoryStatus.LINKED,
                    "SYNC"
            );
        }

        TiendanubePublishResultResponse result = publishInventory(inventoryId);

        return new TiendanubeRetryResponse(
                inventoryId,
                result.status(),
                "PUBLISH"
        );
    }

    @Override
    @Transactional(readOnly = true)
    public TiendanubeInventoryStatusResponse getInventoryStatus(Long inventoryId) {
        Inventory inventory = getInventory(inventoryId);

        log.info(
                "Estado Tiendanube inventario. inventoryId={}, status={}",
                inventoryId,
                inventory.getTiendanubeStatus()
        );

        Long bookstoreId = inventory.getBookstore().getId();

        boolean tiendanubeConnected =
                storeRepository
                        .findByBookstoreIdAndActiveTrue(bookstoreId)
                        .isPresent();

        if (!tiendanubeConnected) {
            return new TiendanubeInventoryStatusResponse(
                    inventoryId,
                    TiendanubeInventoryStatus.NOT_CONNECTED,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }

        return productLinkRepository
                .findByInventoryIdAndActiveTrue(inventoryId)
                .map(link ->
                        new TiendanubeInventoryStatusResponse(
                                inventoryId,
                                inventory.getTiendanubeStatus(),
                                link.getTiendanubeProductId(),
                                link.getTiendanubeVariantId(),
                                link.getSku(),
                                link.getLastSyncedAt(),
                                link.getLastError()
                        )
                )
                .orElseGet(() ->
                        new TiendanubeInventoryStatusResponse(
                                inventoryId,
                                resolveUnlinkedStatus(inventory),
                                null,
                                null,
                                null,
                                null,
                                null
                        )
                );
    }

    @Override
    @Transactional
    public void unlinkInventory(Long inventoryId) {

        Inventory inventory = getInventory(inventoryId);

        TiendanubeProductLink link =
                productLinkRepository
                        .findByInventoryIdAndActiveTrue(inventoryId)
                        .orElseThrow(() ->
                                new BusinessException(
                                        "El inventario no tiene una publicación de Tiendanube vinculada."
                                )
                        );

        link.setActive(false);
        link.setLastError(null);

        inventory.setTiendanubeStatus(
                TiendanubeInventoryStatus.NOT_PUBLISHED
        );

        inventory.setTiendanubePriceSyncEnabled(false);

        log.info(
                "Inventario desvinculado de Tiendanube. "
                        + "inventoryId={} productId={} variantId={}",
                inventoryId,
                link.getTiendanubeProductId(),
                link.getTiendanubeVariantId()
        );
    }

    @Override
    @Transactional
    public void deletePublication(Long inventoryId) {

        Inventory inventory = getInventory(inventoryId);

        TiendanubeProductLink link =
                productLinkRepository
                        .findByInventoryIdAndActiveTrue(inventoryId)
                        .orElseThrow(() ->
                                new BusinessException(
                                        "El inventario no tiene una publicación de Tiendanube vinculada."
                                )
                        );

        TiendanubeStore store =
                getActiveStore(
                        inventory.getBookstore().getId()
                );

        client.deleteProduct(
                store.getStoreId(),
                link.getTiendanubeProductId()
        );

        link.setActive(false);
        link.setLastError(null);

        inventory.setTiendanubeStatus(
                TiendanubeInventoryStatus.NOT_PUBLISHED
        );

        inventory.setTiendanubePriceSyncEnabled(false);

        log.info(
                "Publicación eliminada de Tiendanube. "
                        + "inventoryId={} productId={}",
                inventoryId,
                link.getTiendanubeProductId()
        );
    }

    private void validateCanPublish(Inventory inventory, Long storeId) {
        if (productLinkRepository.findByInventoryIdAndTiendanubeStoreIdAndActiveTrue(inventory.getId(), storeId).isPresent()) {
            throw new BusinessException("El inventario ya tiene una publicación vinculada en Tiendanube");
        }

        TiendanubeInventoryStatus status = inventory.getTiendanubeStatus();

        if (status == TiendanubeInventoryStatus.LINKED || status == TiendanubeInventoryStatus.PUBLISHING) {
            throw new BusinessException("El inventario no puede publicarse en su estado actual: " + status);
        }

        if (inventory.getStock() == null || inventory.getStock() < 0) {
            throw new BusinessException("El inventario no tiene un stock válido");
        }

        if (inventory.getSalePrice() == null || inventory.getSalePrice().signum() <= 0) {
            throw new BusinessException("El inventario debe tener un precio de venta mayor que cero");
        }

        if (inventory.getBook().getTitle() == null || inventory.getBook().getTitle().isBlank()) {
            throw new BusinessException("El libro no tiene título");
        }
    }

    private Inventory getInventory(Long inventoryId) {

        return inventoryRepository
                .findById(inventoryId)
                .orElseThrow(() -> new BusinessException("No existe el inventario con id " + inventoryId));
    }

    private TiendanubeStore getActiveStore(Long bookstoreId) {

        return storeRepository
                .findByBookstoreIdAndActiveTrue(bookstoreId)
                .orElseThrow(() -> new BusinessException("La librería no tiene una cuenta Tiendanube vinculada"));
    }

    private TiendanubeVariantResponse getMainVariant(TiendanubeProductResponse product) {

        if (product.variants() == null || product.variants().isEmpty()) {

            throw new BusinessException("Tiendanube creó el producto sin variantes");
        }

        return product.variants().getFirst();
    }

    private TiendanubeInventoryStatus resolveUnlinkedStatus(
            Inventory inventory
    ) {
        TiendanubeInventoryStatus status =
                inventory.getTiendanubeStatus();

        if (
                status == TiendanubeInventoryStatus.PENDING_PUBLICATION
                        || status == TiendanubeInventoryStatus.PUBLISHING
                        || status == TiendanubeInventoryStatus.LINK_REQUIRED
                        || status == TiendanubeInventoryStatus.SYNC_ERROR
                        || status == TiendanubeInventoryStatus.REMOTE_PRODUCT_NOT_FOUND
        ) {
            return status;
        }

        return TiendanubeInventoryStatus.NOT_PUBLISHED;
    }
}