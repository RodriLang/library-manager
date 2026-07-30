package com.rodrilang.librarymanager.integrations.tiendanube.service.impl;

import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeProductLink;
import com.rodrilang.librarymanager.integrations.tiendanube.enums.TiendanubeInventoryStatus;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeProductLinkRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.service.TiendanubeInventoryStateService;
import com.rodrilang.librarymanager.model.Inventory;
import com.rodrilang.librarymanager.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TiendanubeInventoryStateServiceImpl implements TiendanubeInventoryStateService {

    private final InventoryRepository inventoryRepository;
    private final TiendanubeProductLinkRepository productLinkRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateStatus(Long inventoryId, TiendanubeInventoryStatus status) {
        Inventory inventory = inventoryRepository.findById(inventoryId).orElseThrow();
        inventory.setTiendanubeStatus(status);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSyncError(Long inventoryId) {
        Inventory inventory = inventoryRepository.findById(inventoryId).orElseThrow();
        inventory.setTiendanubeStatus(TiendanubeInventoryStatus.SYNC_ERROR);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSyncError(Long inventoryId, Long linkId, String error) {
        Inventory inventory = inventoryRepository.findById(inventoryId).orElseThrow();
        TiendanubeProductLink link = productLinkRepository.findById(linkId).orElseThrow();

        inventory.setTiendanubeStatus(TiendanubeInventoryStatus.SYNC_ERROR);
        link.setLastError(error);
    }
}