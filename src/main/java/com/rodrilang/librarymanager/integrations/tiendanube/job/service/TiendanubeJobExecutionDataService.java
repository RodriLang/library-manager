package com.rodrilang.librarymanager.integrations.tiendanube.job.service;

import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.request.TiendanubeUpdateVariantRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeProductLink;
import com.rodrilang.librarymanager.integrations.tiendanube.enums.TiendanubeInventoryStatus;
import com.rodrilang.librarymanager.integrations.tiendanube.factory.TiendanubeProductRequestFactory;
import com.rodrilang.librarymanager.integrations.tiendanube.job.dto.execution.TiendanubeDeletePublicationSnapshot;
import com.rodrilang.librarymanager.integrations.tiendanube.job.dto.execution.TiendanubeLinkedInventorySnapshot;
import com.rodrilang.librarymanager.integrations.tiendanube.job.dto.execution.TiendanubePublicationSnapshot;
import com.rodrilang.librarymanager.integrations.tiendanube.job.dto.execution.TiendanubePublishSnapshot;
import com.rodrilang.librarymanager.integrations.tiendanube.job.exception.TiendanubeJobExecutionException;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeProductLinkRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.util.TiendanubeProductUtils;
import com.rodrilang.librarymanager.model.Inventory;
import com.rodrilang.librarymanager.repository.InventoryRepository;
import com.rodrilang.librarymanager.util.TextNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TiendanubeJobExecutionDataService {

    private final InventoryRepository inventoryRepository;
    private final TiendanubeProductLinkRepository productLinkRepository;
    private final TiendanubeProductRequestFactory productRequestFactory;

    @Transactional(readOnly = true)
    public Optional<TiendanubeLinkedInventorySnapshot> prepareLinkedInventory(Long inventoryId, Long expectedStoreId) {
        return productLinkRepository.findWithInventoryBookByInventoryIdAndActiveTrue(inventoryId)
                .map(link -> toLinkedSnapshot(link, expectedStoreId));
    }

    @Transactional(readOnly = true)
    public Optional<TiendanubePublicationSnapshot> preparePublication(Long inventoryId, Long expectedStoreId) {
        return productLinkRepository.findWithInventoryBookByInventoryIdAndActiveTrue(inventoryId)
                .map(link -> {
                    TiendanubeLinkedInventorySnapshot linked = toLinkedSnapshot(link, expectedStoreId);
                    return new TiendanubePublicationSnapshot(
                            linked,
                            productRequestFactory.updateProduct(link.getInventory()),
                            link.getInventory().getBook().getCoverUrl(),
                            link.getLastSyncedCoverUrl()
                    );
                });
    }

    @Transactional(readOnly = true)
    public Optional<TiendanubePublishSnapshot> preparePublish(Long inventoryId, Long expectedStoreId) {
        if (productLinkRepository.findByInventoryIdAndActiveTrue(inventoryId).isPresent()) {
            return Optional.empty();
        }

        Inventory inventory = inventoryRepository.findByIdForTiendanubePublish(inventoryId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el inventario."));

        validatePublishable(inventory);

        String isbn = TiendanubeProductUtils.normalizeIdentifier(inventory.getBook().getPreferredIsbn());
        String titleSearch = inventory.getBook().getTitleSearch();

        if (titleSearch == null || titleSearch.isBlank()) {
            titleSearch = TextNormalizer.normalizeForSearch(inventory.getBook().getTitle());
        }

        String sku = isbn != null ? isbn : "LM-" + inventoryId;
        TiendanubeUpdateVariantRequest variantRequest = new TiendanubeUpdateVariantRequest(
                sku,
                isbn,
                inventory.getSalePrice(),
                inventory.getStock(),
                true,
                inventory.getBook().getWeightGrams(),
                inventory.getBook().getWidthCm(),
                inventory.getBook().getHeightCm(),
                inventory.getBook().getDepthCm()
        );

        return Optional.of(new TiendanubePublishSnapshot(
                inventoryId,
                inventory.getBookstore().getId(),
                expectedStoreId,
                isbn,
                titleSearch,
                inventory.getBook().getCoverUrl(),
                productRequestFactory.createProduct(inventory),
                variantRequest
        ));
    }

    @Transactional(readOnly = true)
    public Optional<TiendanubeDeletePublicationSnapshot> prepareDelete(Long inventoryId, Long expectedStoreId) {
        return productLinkRepository.findByInventoryIdAndActiveTrue(inventoryId)
                .map(link -> {
                    validateExpectedStore(link, expectedStoreId);
                    return new TiendanubeDeletePublicationSnapshot(
                            inventoryId,
                            link.getId(),
                            link.getTiendanubeStoreId(),
                            link.getTiendanubeProductId()
                    );
                });
    }

    private TiendanubeLinkedInventorySnapshot toLinkedSnapshot(TiendanubeProductLink link, Long expectedStoreId) {
        validateExpectedStore(link, expectedStoreId);
        Inventory inventory = link.getInventory();
        validateSyncStatus(inventory);

        String isbn = TiendanubeProductUtils.normalizeIdentifier(inventory.getBook().getPreferredIsbn());
        String resolvedSku = link.getSku();

        if ((resolvedSku == null || resolvedSku.isBlank()) && isbn != null) {
            resolvedSku = isbn;
        }

        TiendanubeUpdateVariantRequest fullVariantRequest = new TiendanubeUpdateVariantRequest(
                resolvedSku,
                isbn,
                inventory.getSalePrice(),
                inventory.getStock(),
                true,
                inventory.getBook().getWeightGrams(),
                inventory.getBook().getWidthCm(),
                inventory.getBook().getHeightCm(),
                inventory.getBook().getDepthCm()
        );

        return new TiendanubeLinkedInventorySnapshot(
                inventory.getId(),
                link.getId(),
                link.getTiendanubeStoreId(),
                link.getTiendanubeProductId(),
                link.getTiendanubeVariantId(),
                inventory.getStock(),
                inventory.getSalePrice(),
                link.getSku(),
                resolvedSku,
                fullVariantRequest
        );
    }

    private void validateExpectedStore(TiendanubeProductLink link, Long expectedStoreId) {
        if (!expectedStoreId.equals(link.getTiendanubeStoreId())) {
            throw TiendanubeJobExecutionException.blocked(
                    "LINK_STORE_CHANGED",
                    "El vínculo del inventario pertenece a otra cuenta Tiendanube",
                    null,
                    null
            );
        }
    }

    private void validateSyncStatus(Inventory inventory) {
        TiendanubeInventoryStatus status = inventory.getTiendanubeStatus();

        if (status != TiendanubeInventoryStatus.LINKED && status != TiendanubeInventoryStatus.SYNC_ERROR) {
            throw TiendanubeJobExecutionException.nonRetryable(
                    "INVALID_INVENTORY_STATUS",
                    "El inventario no puede sincronizarse en estado " + status,
                    null
            );
        }
    }

    private void validatePublishable(Inventory inventory) {
        TiendanubeInventoryStatus status = inventory.getTiendanubeStatus();

        if (status == TiendanubeInventoryStatus.LINKED) {
            throw TiendanubeJobExecutionException.nonRetryable(
                    "ALREADY_LINKED", "El inventario ya está vinculado con Tiendanube", null
            );
        }

        if (inventory.getStock() == null || inventory.getStock() < 0) {
            throw TiendanubeJobExecutionException.nonRetryable(
                    "INVALID_STOCK", "El inventario no tiene un stock válido", null
            );
        }

        if (inventory.getSalePrice() == null || inventory.getSalePrice().signum() <= 0) {
            throw TiendanubeJobExecutionException.nonRetryable(
                    "INVALID_PRICE", "El inventario debe tener un precio de venta mayor que cero", null
            );
        }

        if (inventory.getBook().getTitle() == null || inventory.getBook().getTitle().isBlank()) {
            throw TiendanubeJobExecutionException.nonRetryable(
                    "INVALID_TITLE", "El libro no tiene título", null
            );
        }
    }
}
