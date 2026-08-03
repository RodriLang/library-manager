package com.rodrilang.librarymanager.integrations.tiendanube.service.impl;

import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.integrations.tiendanube.client.TiendanubeClient;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.internal.RemoteInventoryMatch;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.request.TiendanubeCreateImageRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.request.TiendanubeCreateProductRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.request.TiendanubeCreateVariantRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeInventoryStatusResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeProductLinkResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeProductResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubePublishResultResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeRemoteProductResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeRetryResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeVariantResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeStore;
import com.rodrilang.librarymanager.integrations.tiendanube.enums.TiendanubeInventoryStatus;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeProductLinkRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeStoreRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.service.TiendanubeInventoryStateService;
import com.rodrilang.librarymanager.integrations.tiendanube.service.TiendanubeProductLinkPersistenceService;
import com.rodrilang.librarymanager.integrations.tiendanube.service.TiendanubeProductLinkService;
import com.rodrilang.librarymanager.integrations.tiendanube.service.TiendanubeProductMatchingService;
import com.rodrilang.librarymanager.integrations.tiendanube.service.TiendanubeProductService;
import com.rodrilang.librarymanager.integrations.tiendanube.service.TiendanubeVariantSyncService;
import com.rodrilang.librarymanager.integrations.tiendanube.util.TiendanubeProductUtils;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.model.Inventory;
import com.rodrilang.librarymanager.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TiendanubeProductServiceImpl implements TiendanubeProductService {

    private final InventoryRepository inventoryRepository;
    private final TiendanubeStoreRepository storeRepository;
    private final TiendanubeProductLinkRepository productLinkRepository;
    private final TiendanubeClient client;
    private final TiendanubeVariantSyncService variantSyncService;
    private final TiendanubeInventoryStateService inventoryStateService;
    private final TiendanubeProductLinkPersistenceService linkPersistenceService;
    private final TiendanubeProductLinkService productLinkService;
    private final TiendanubeProductMatchingService matchingService;

    @Override
    public TiendanubePublishResultResponse publishInventory(Long inventoryId) {
        Inventory inventory = getInventory(inventoryId);
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
            TiendanubeCreateProductRequest request = buildCreateProductRequest(inventory);
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
    public List<TiendanubeRemoteProductResponse> getRemoteProducts(Long bookstoreId) {
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
                        inventoryId,
                        inventory.getTiendanubeStatus(),
                        null,
                        null,
                        null,
                        null,
                        null
                ));
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

    private TiendanubeCreateProductRequest buildCreateProductRequest(Inventory inventory) {
        Book book = inventory.getBook();

        String sku = buildSku(inventory);
        String isbn = TiendanubeProductUtils.normalizeIdentifier(book.getPreferredIsbn());

        TiendanubeCreateVariantRequest variant = new TiendanubeCreateVariantRequest(
                inventory.getSalePrice(),
                inventory.getStock(),
                sku,
                isbn,
                book.getWeightGrams(),
                book.getWidthCm(),
                book.getHeightCm(),
                book.getDepthCm()
        );

        List<TiendanubeCreateImageRequest> images =
                book.getCoverUrl() == null || book.getCoverUrl().isBlank()
                        ? List.of()
                        : List.of(new TiendanubeCreateImageRequest(book.getCoverUrl()));

        return new TiendanubeCreateProductRequest(
                Map.of("es", book.getTitle()),
                buildDescription(book),
                List.of(variant),
                images,
                true
        );
    }

    private String buildSku(Inventory inventory) {

        String isbn = TiendanubeProductUtils.normalizeIdentifier(inventory.getBook().getPreferredIsbn());

        if (isbn != null) {
            return isbn;
        }

        return "LM-" + inventory.getId();
    }

    private Map<String, String> buildDescription(Book book) {

        if (book.getDescription() == null
                || book.getDescription().isBlank()) {

            return Map.of();
        }

        return Map.of(
                "es",
                book.getDescription()
        );
    }

    private TiendanubeVariantResponse getMainVariant(TiendanubeProductResponse product) {

        if (product.variants() == null || product.variants().isEmpty()) {

            throw new BusinessException("Tiendanube creó el producto sin variantes");
        }

        return product.variants().getFirst();
    }
}