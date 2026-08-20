package com.rodrilang.librarymanager.integrations.tiendanube.service.impl;

import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.integrations.tiendanube.client.TiendanubeClient;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.request.TiendanubeCreateImageRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeImageResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeProductResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeProductLink;
import com.rodrilang.librarymanager.integrations.tiendanube.factory.TiendanubeProductRequestFactory;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeProductLinkRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.service.TiendanubeProductSyncService;
import com.rodrilang.librarymanager.integrations.tiendanube.service.TiendanubeVariantSyncService;
import com.rodrilang.librarymanager.model.Inventory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class TiendanubeProductSyncServiceImpl implements TiendanubeProductSyncService {

    private final TiendanubeProductLinkRepository productLinkRepository;
    private final TiendanubeProductRequestFactory productRequestFactory;
    private final TiendanubeClient client;
    private final TiendanubeVariantSyncService variantSyncService;

    @Override
    public void syncMissingImage(Long inventoryId) {
        TiendanubeProductLink link = productLinkRepository.findWithInventoryBookByInventoryIdAndActiveTrue(inventoryId)
                .orElseThrow(() -> new BusinessException("El inventario no tiene vínculo activo con Tiendanube"));

        Inventory inventory = link.getInventory();
        String coverUrl = inventory.getBook().getCoverUrl();

        if (coverUrl == null || coverUrl.isBlank()) {
            return;
        }

        TiendanubeProductResponse product = client.getProduct(
                link.getTiendanubeStoreId(),
                link.getTiendanubeProductId()
        );

        if (product.images() != null && !product.images().isEmpty()) {
            return;
        }

        client.createProductImage(
                link.getTiendanubeStoreId(),
                link.getTiendanubeProductId(),
                new TiendanubeCreateImageRequest(coverUrl, 1)
        );

        log.info("Imagen agregada a Tiendanube. inventoryId={}, productId={}",
                inventoryId, link.getTiendanubeProductId());
    }

    @Override
    public void syncAfterImport(Long inventoryId) {
        variantSyncService.syncVariant(inventoryId);
        syncMissingImage(inventoryId);
    }

    @Override
    @Transactional
    public void syncPublication(Long inventoryId) {

        TiendanubeProductLink link =
                productLinkRepository
                        .findWithInventoryBookByInventoryIdAndActiveTrue(inventoryId)
                        .orElseThrow(() ->
                                new BusinessException(
                                        "El inventario no tiene vínculo activo con Tiendanube"
                                )
                        );

        Inventory inventory = link.getInventory();

        client.updateProduct(
                link.getTiendanubeStoreId(),
                link.getTiendanubeProductId(),
                productRequestFactory.updateProduct(inventory)
        );

        variantSyncService.syncVariant(inventoryId);

        syncCover(link);

        log.info(
                "Publicación sincronizada con Tiendanube. inventoryId={}, productId={}",
                inventoryId,
                link.getTiendanubeProductId()
        );
    }

    private void syncCover(TiendanubeProductLink link) {


        Inventory inventory = link.getInventory();

        String coverUrl = inventory.getBook().getCoverUrl();

        if (coverUrl == null || coverUrl.isBlank()) {
            return;
        }

        if (Objects.equals(coverUrl, link.getLastSyncedCoverUrl())) {
            return;
        }

        TiendanubeProductResponse product = client.getProduct(link.getTiendanubeStoreId(), link.getTiendanubeProductId());

        TiendanubeImageResponse currentMainImage = findMainImage(product);

        TiendanubeImageResponse newImage =
                client.createProductImage(
                        link.getTiendanubeStoreId(),
                        link.getTiendanubeProductId(),
                        new TiendanubeCreateImageRequest(coverUrl, 1)
                );

        if (newImage == null) {
            throw new BusinessException("Tiendanube no devolvió la imagen creada");
        }

        if (currentMainImage != null) {
            client.deleteProductImage(
                    link.getTiendanubeStoreId(),
                    link.getTiendanubeProductId(),
                    currentMainImage.id()
            );
        }

        log.info(
                "Portada sincronizada con Tiendanube. inventoryId={}, productId={}, imageId={}",
                inventory.getId(),
                link.getTiendanubeProductId(),
                newImage.id()
        );
    }

    private void createCover(
            TiendanubeProductLink link,
            String coverUrl
    ) {
        client.createProductImage(
                link.getTiendanubeStoreId(),
                link.getTiendanubeProductId(),
                new TiendanubeCreateImageRequest(coverUrl, 1)
        );
    }

    private TiendanubeImageResponse findMainImage(TiendanubeProductResponse product) {
        if (product.images() == null || product.images().isEmpty()) {
            return null;
        }

        return product.images()
                .stream()
                .filter(image -> Integer.valueOf(1).equals(image.position()))
                .findFirst()
                .orElse(product.images().getFirst());
    }
}