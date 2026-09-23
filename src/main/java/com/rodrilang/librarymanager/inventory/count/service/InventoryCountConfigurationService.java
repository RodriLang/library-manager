package com.rodrilang.librarymanager.inventory.count.service;

import com.rodrilang.librarymanager.enums.BookCondition;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.inventory.count.dto.request.UpdateInventoryCountConfigurationRequest;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountItem;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountItemStatus;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountSession;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountStatus;
import com.rodrilang.librarymanager.inventory.count.repository.InventoryCountItemRepository;
import com.rodrilang.librarymanager.model.Inventory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InventoryCountConfigurationService {

    private final InventoryCountItemRepository itemRepository;
    private final InventoryCountPriceResolver priceResolver;
    private final InventoryCountReviewResetService reviewResetService;
    private final InventoryCountPendingApplyService pendingApplyService;

    public void update(InventoryCountSession session, UpdateInventoryCountConfigurationRequest request) {
        requireConfigurable(session);

        if (Boolean.TRUE.equals(request.editorialPriceSyncEnabled()) && session.getCondition() != BookCondition.NEW) {
            throw new BusinessException("La sincronización con precio editorial solo está disponible para libros nuevos");
        }

        boolean previousDefaultEditorialSync = Boolean.TRUE.equals(session.getDefaultEditorialPriceSyncEnabled());

        if (request.editorialPriceSyncEnabled() != null) {
            session.setDefaultEditorialPriceSyncEnabled(request.editorialPriceSyncEnabled());
        }
        if (request.publishOnTiendanube() != null) {
            session.setDefaultPublishOnTiendanube(request.publishOnTiendanube());
        }
        if (request.tiendanubePriceSyncEnabled() != null) {
            session.setDefaultTiendanubePriceSyncEnabled(request.tiendanubePriceSyncEnabled());
        }
        if (request.minimumStock() != null) {
            session.setDefaultMinimumStock(request.minimumStock());
        }

        if (session.getStatus() == InventoryCountStatus.REVIEW) {
            reviewResetService.resetToOpen(session);
        }

        if (!request.applyToAll()) {
            return;
        }

        List<InventoryCountItem> items = itemRepository.findAllBySessionIdOrderById(session.getId());
        List<Long> bookIds = items.stream()
                .filter(item -> item.getBook() != null && item.getAppliedAt() == null)
                .map(item -> item.getBook().getId())
                .distinct()
                .toList();
        Map<Long, BigDecimal> editorialPrices = priceResolver.currentEditorialPrices(bookIds);
        Map<Long, Inventory> inventories = priceResolver.existingInventories(session, bookIds);

        for (InventoryCountItem item : items) {
            if (item.getAppliedAt() != null || item.getStatus() == InventoryCountItemStatus.SUPERSEDED) {
                continue;
            }

            Inventory inventory = item.getBook() != null ? inventories.get(item.getBook().getId()) : null;
            BigDecimal editorialPrice = item.getBook() != null ? editorialPrices.get(item.getBook().getId()) : null;

            // Las elecciones globales también se copian a candidatos todavía sin resolver
            // para que la intención sobreviva hasta que exista un libro de catálogo.
            if (request.editorialPriceSyncEnabled() != null) {
                boolean wasEditorialSync = effectiveEditorialSync(
                        item,
                        inventory,
                        previousDefaultEditorialSync,
                        editorialPrice
                );

                if (editorialPrice != null
                        && (Boolean.TRUE.equals(request.editorialPriceSyncEnabled()) || wasEditorialSync)) {
                    // Al activar se toma el precio editorial ahora. Al desactivar se congela
                    // el valor editorial vigente como precio independiente.
                    item.setSalePriceOverride(editorialPrice);
                }
                item.setEditorialPriceSyncOverride(request.editorialPriceSyncEnabled());
            }
            if (request.publishOnTiendanube() != null) {
                item.setPublishOnTiendanubeOverride(request.publishOnTiendanube());
            }
            if (request.tiendanubePriceSyncEnabled() != null) {
                item.setTiendanubePriceSyncOverride(request.tiendanubePriceSyncEnabled());
            }
            if (request.minimumStock() != null) {
                item.setMinimumStockOverride(request.minimumStock());
            }

            if (item.getBook() == null) {
                itemRepository.save(item);
                continue;
            }

            refreshStatus(item, inventory, editorialPrice);
            itemRepository.save(item);

            if (session.getStatus() == InventoryCountStatus.APPLIED_WITH_PENDING) {
                pendingApplyService.applyIfReady(item);
            }
        }
    }

    private boolean effectiveEditorialSync(
            InventoryCountItem item,
            Inventory inventory,
            boolean previousDefaultEditorialSync,
            BigDecimal editorialPrice
    ) {
        if (item.getSession().getCondition() != BookCondition.NEW || editorialPrice == null) {
            return false;
        }
        if (item.getEditorialPriceSyncOverride() != null) {
            return Boolean.TRUE.equals(item.getEditorialPriceSyncOverride());
        }
        if (inventory != null) {
            return Boolean.TRUE.equals(inventory.getEditorialPriceSyncEnabled());
        }
        return previousDefaultEditorialSync;
    }

    private void refreshStatus(InventoryCountItem item, Inventory inventory, BigDecimal editorialPrice) {
        boolean hasPrice = item.getSalePriceOverride() != null
                || inventory != null
                || editorialPrice != null;
        item.setStatus(hasPrice ? InventoryCountItemStatus.RESOLVED : InventoryCountItemStatus.PENDING_PRICE);
    }

    private void requireConfigurable(InventoryCountSession session) {
        if (session.getStatus() == InventoryCountStatus.OPEN
                || session.getStatus() == InventoryCountStatus.REVIEW
                || session.getStatus() == InventoryCountStatus.APPLIED_WITH_PENDING) {
            return;
        }
        throw new BusinessException("La configuración de esta carga ya no puede modificarse");
    }
}
