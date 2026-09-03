package com.rodrilang.librarymanager.integrations.tiendanube.job.service;

import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeProductResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeVariantResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeProductLink;
import com.rodrilang.librarymanager.integrations.tiendanube.enums.TiendanubeInventoryStatus;
import com.rodrilang.librarymanager.integrations.tiendanube.job.dto.execution.TiendanubeCoverSyncResult;
import com.rodrilang.librarymanager.integrations.tiendanube.job.dto.execution.TiendanubeLinkedInventorySnapshot;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeProductLinkRepository;
import com.rodrilang.librarymanager.model.Inventory;
import com.rodrilang.librarymanager.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class TiendanubeJobResultService {

    private final InventoryRepository inventoryRepository;
    private final TiendanubeProductLinkRepository productLinkRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registerLinkedSuccess(TiendanubeLinkedInventorySnapshot snapshot) {
        TiendanubeProductLink link = requireActiveLink(snapshot.linkId());
        Inventory inventory = requireInventory(snapshot.inventoryId());

        if ((link.getSku() == null || link.getSku().isBlank()) && snapshot.resolvedSku() != null) {
            link.setSku(snapshot.resolvedSku());
        }

        link.setLastSyncedAt(Instant.now());
        link.setLastError(null);
        inventory.setTiendanubeStatus(TiendanubeInventoryStatus.LINKED);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registerPublicationSuccess(TiendanubeLinkedInventorySnapshot snapshot, TiendanubeCoverSyncResult cover) {
        TiendanubeProductLink link = requireActiveLink(snapshot.linkId());
        Inventory inventory = requireInventory(snapshot.inventoryId());

        if ((link.getSku() == null || link.getSku().isBlank()) && snapshot.resolvedSku() != null) {
            link.setSku(snapshot.resolvedSku());
        }

        if (cover.changed()) {
            link.setLastSyncedCoverUrl(cover.coverUrl());
            link.setTiendanubeImageId(cover.imageId());
        }

        clearPendingCoverSync(link);
        link.setLastSyncedAt(Instant.now());
        link.setLastError(null);
        inventory.setTiendanubeStatus(TiendanubeInventoryStatus.LINKED);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registerFailure(Long inventoryId, Long linkId, RuntimeException exception) {
        Inventory inventory = requireInventory(inventoryId);
        inventory.setTiendanubeStatus(TiendanubeInventoryStatus.SYNC_ERROR);

        if (linkId != null) {
            productLinkRepository.findByIdForUpdate(linkId).ifPresent(link -> link.setLastError(resolveError(exception)));
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registerRemoteNotFound(Long inventoryId, Long linkId, RuntimeException exception) {
        Inventory inventory = requireInventory(inventoryId);
        inventory.setTiendanubeStatus(TiendanubeInventoryStatus.REMOTE_PRODUCT_NOT_FOUND);

        if (linkId != null) {
            productLinkRepository.findByIdForUpdate(linkId).ifPresent(link -> link.setLastError(resolveError(exception)));
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPublishing(Long inventoryId) {
        requireInventory(inventoryId).setTiendanubeStatus(TiendanubeInventoryStatus.PUBLISHING);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markLinkRequired(Long inventoryId) {
        requireInventory(inventoryId).setTiendanubeStatus(TiendanubeInventoryStatus.LINK_REQUIRED);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAlreadyLinked(Long inventoryId) {
        requireInventory(inventoryId).setTiendanubeStatus(TiendanubeInventoryStatus.LINKED);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markNotPublished(Long inventoryId) {
        Inventory inventory = requireInventory(inventoryId);
        inventory.setTiendanubeStatus(TiendanubeInventoryStatus.NOT_PUBLISHED);
        inventory.setTiendanubePriceSyncEnabled(false);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void savePublishedLink(Long inventoryId, Long storeId, TiendanubeProductResponse product,
                                  TiendanubeVariantResponse variant, String coverUrl, String fallbackSku) {
        Inventory inventory = requireInventory(inventoryId);
        TiendanubeProductLink existing = productLinkRepository.findByInventoryIdAndActiveTrue(inventoryId).orElse(null);

        if (existing != null) {
            inventory.setTiendanubeStatus(TiendanubeInventoryStatus.LINKED);
            return;
        }

        boolean hasImage = product.images() != null && !product.images().isEmpty();
        Long imageId = hasImage ? product.images().getFirst().id() : null;

        TiendanubeProductLink link = TiendanubeProductLink.builder()
                .inventory(inventory)
                .tiendanubeStoreId(storeId)
                .tiendanubeProductId(product.id())
                .tiendanubeVariantId(variant.id())
                .tiendanubeImageId(imageId)
                .lastSyncedCoverUrl(hasImage && coverUrl != null && !coverUrl.isBlank() ? coverUrl : null)
                .sku(variant.sku() == null || variant.sku().isBlank() ? fallbackSku : variant.sku())
                .active(true)
                .lastSyncedAt(Instant.now())
                .lastError(null)
                .build();

        productLinkRepository.save(link);
        inventory.setTiendanubeStatus(TiendanubeInventoryStatus.LINKED);
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public void validateVariantAvailable(Long storeId, Long variantId, Long inventoryId) {
        productLinkRepository.findByTiendanubeStoreIdAndTiendanubeVariantIdAndActiveTrue(storeId, variantId)
                .filter(link -> !link.getInventory().getId().equals(inventoryId))
                .ifPresent(link -> {
                    throw new BusinessException("La variante de Tiendanube ya está vinculada a otro inventario");
                });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveExistingLink(Long inventoryId, Long storeId, Long productId, TiendanubeVariantResponse variant,
                                 String finalSku) {
        Inventory inventory = requireInventory(inventoryId);
        TiendanubeProductLink existing = productLinkRepository.findByInventoryIdAndActiveTrue(inventoryId).orElse(null);

        if (existing != null) {
            inventory.setTiendanubeStatus(TiendanubeInventoryStatus.LINKED);
            return;
        }

        productLinkRepository.findByTiendanubeStoreIdAndTiendanubeVariantIdAndActiveTrue(storeId, variant.id())
                .ifPresent(link -> {
                    throw new BusinessException("La variante de Tiendanube ya está vinculada a otro inventario");
                });

        TiendanubeProductLink link = TiendanubeProductLink.builder()
                .inventory(inventory)
                .tiendanubeStoreId(storeId)
                .tiendanubeProductId(productId)
                .tiendanubeVariantId(variant.id())
                .sku(finalSku)
                .active(true)
                .lastSyncedAt(Instant.now())
                .lastError(null)
                .build();

        productLinkRepository.save(link);
        inventory.setTiendanubeStatus(TiendanubeInventoryStatus.LINKED);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registerDeleteSuccess(Long inventoryId, Long linkId) {
        Inventory inventory = requireInventory(inventoryId);
        TiendanubeProductLink link = productLinkRepository.findByIdForUpdate(linkId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el vínculo de Tiendanube"));

        link.setActive(false);
        link.setLastError(null);
        clearPendingCoverSync(link);
        inventory.setTiendanubeStatus(TiendanubeInventoryStatus.NOT_PUBLISHED);
        inventory.setTiendanubePriceSyncEnabled(false);
    }


    private void clearPendingCoverSync(TiendanubeProductLink link) {
        link.setPendingCoverUrl(null);
        link.setPendingCoverExistingImageIds(null);
        link.setPendingCoverStartedAt(null);
    }

    private Inventory requireInventory(Long inventoryId) {
        return inventoryRepository.findById(inventoryId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe el inventario con id: " + inventoryId));
    }

    private TiendanubeProductLink requireActiveLink(Long linkId) {
        TiendanubeProductLink link = productLinkRepository.findByIdForUpdate(linkId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el vínculo de Tiendanube"));

        if (!link.isActive()) {
            throw new BusinessException("El vínculo de Tiendanube ya no está activo");
        }

        return link;
    }

    private String resolveError(RuntimeException exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }
}
