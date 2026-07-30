package com.rodrilang.librarymanager.integrations.tiendanube.service.impl;

import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.integrations.tiendanube.client.TiendanubeClient;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.request.TiendanubeUpdateVariantRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeProductLinkResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeProductResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeVariantResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeProductLink;
import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeStore;
import com.rodrilang.librarymanager.integrations.tiendanube.enums.TiendanubeInventoryStatus;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeProductLinkRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeStoreRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.service.TiendanubeProductLinkService;
import com.rodrilang.librarymanager.model.Inventory;
import com.rodrilang.librarymanager.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class TiendanubeProductLinkServiceImpl implements TiendanubeProductLinkService {

    private final InventoryRepository inventoryRepository;
    private final TiendanubeStoreRepository storeRepository;
    private final TiendanubeProductLinkRepository productLinkRepository;
    private final TiendanubeClient client;

    @Override
    @Transactional
    public TiendanubeProductLinkResponse linkExistingProduct(
            Long inventoryId,
            Long productId,
            Long variantId
    ) {
        Inventory inventory = inventoryRepository.findById(inventoryId)
                .orElseThrow(() -> new BusinessException("No existe el inventario con id " + inventoryId));

        TiendanubeStore store = storeRepository.findByBookstoreIdAndActiveTrue(inventory.getBookstore().getId())
                .orElseThrow(() -> new BusinessException("La librería no tiene una cuenta Tiendanube vinculada"));

        validateCanLink(inventoryId, store.getStoreId(), variantId);

        TiendanubeProductResponse remoteProduct = client.getProduct(store.getStoreId(), productId);
        TiendanubeVariantResponse remoteVariant = findVariant(remoteProduct, variantId);

        String finalSku = updateRemoteVariantOnLink(
                store.getStoreId(),
                productId,
                remoteVariant,
                inventory
        );

        TiendanubeProductLink link = TiendanubeProductLink.builder()
                .inventory(inventory)
                .tiendanubeStoreId(store.getStoreId())
                .tiendanubeProductId(productId)
                .tiendanubeVariantId(variantId)
                .sku(finalSku)
                .active(true)
                .lastSyncedAt(Instant.now())
                .lastError(null)
                .build();

        productLinkRepository.save(link);

        inventory.setTiendanubeStatus(TiendanubeInventoryStatus.LINKED);

        return new TiendanubeProductLinkResponse(
                inventoryId,
                productId,
                variantId,
                finalSku,
                TiendanubeInventoryStatus.LINKED
        );
    }

    private void validateCanLink(Long inventoryId, Long storeId, Long variantId) {
        if (productLinkRepository.findByInventoryIdAndTiendanubeStoreIdAndActiveTrue(inventoryId, storeId).isPresent()) {
            throw new BusinessException("El inventario ya está vinculado con Tiendanube");
        }

        if (productLinkRepository.findByTiendanubeStoreIdAndTiendanubeVariantIdAndActiveTrue(storeId, variantId).isPresent()) {
            throw new BusinessException("La variante de Tiendanube ya está vinculada a otro inventario");
        }
    }

    private TiendanubeVariantResponse findVariant(TiendanubeProductResponse product, Long variantId) {
        if (product.variants() == null) {
            throw new BusinessException("El producto de Tiendanube no posee variantes");
        }

        return product.variants().stream()
                .filter(variant -> variant.id().equals(variantId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        "La variante " + variantId + " no pertenece al producto " + product.id()
                ));
    }

    private String updateRemoteVariantOnLink(
            Long storeId,
            Long productId,
            TiendanubeVariantResponse remoteVariant,
            Inventory inventory
    ) {
        String isbn = normalizeIdentifier(inventory.getBook().getIsbn());

        boolean missingSku = remoteVariant.sku() == null || remoteVariant.sku().isBlank();
        boolean missingBarcode = remoteVariant.barcode() == null || remoteVariant.barcode().isBlank();

        String sku = missingSku && isbn != null ? isbn : remoteVariant.sku();
        String barcode = missingBarcode && isbn != null ? isbn : remoteVariant.barcode();

        TiendanubeUpdateVariantRequest request = new TiendanubeUpdateVariantRequest(
                sku,
                barcode,
                inventory.getSalePrice(),
                inventory.getStock(),
                true
        );

        client.updateVariant(storeId, productId, remoteVariant.id(), request);

        return sku;
    }

    private String normalizeIdentifier(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.replace("-", "").replace(" ", "").trim();
    }
}