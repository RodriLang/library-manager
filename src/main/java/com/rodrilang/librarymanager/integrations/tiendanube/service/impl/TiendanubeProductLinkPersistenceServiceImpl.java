package com.rodrilang.librarymanager.integrations.tiendanube.service.impl;

import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeVariantResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeProductLink;
import com.rodrilang.librarymanager.integrations.tiendanube.enums.TiendanubeInventoryStatus;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeProductLinkRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.service.TiendanubeProductLinkPersistenceService;
import com.rodrilang.librarymanager.model.Inventory;
import com.rodrilang.librarymanager.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class TiendanubeProductLinkPersistenceServiceImpl
        implements TiendanubeProductLinkPersistenceService {

    private final InventoryRepository inventoryRepository;
    private final TiendanubeProductLinkRepository productLinkRepository;

    @Override
    @Transactional
    public void savePublishedLink(
            Long inventoryId,
            Long storeId,
            Long productId,
            TiendanubeVariantResponse variant
    ) {
        Inventory inventory = inventoryRepository.findById(inventoryId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No existe el inventario con id: " + inventoryId
                ));

        TiendanubeProductLink link = TiendanubeProductLink.builder()
                .inventory(inventory)
                .tiendanubeStoreId(storeId)
                .tiendanubeProductId(productId)
                .tiendanubeVariantId(variant.id())
                .sku(variant.sku())
                .active(true)
                .lastSyncedAt(Instant.now())
                .lastError(null)
                .build();

        productLinkRepository.save(link);

        inventory.setTiendanubeStatus(TiendanubeInventoryStatus.LINKED);
    }
}