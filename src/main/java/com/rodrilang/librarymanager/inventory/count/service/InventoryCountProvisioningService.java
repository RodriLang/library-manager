package com.rodrilang.librarymanager.inventory.count.service;

import com.rodrilang.librarymanager.enums.BookCondition;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.integrations.tiendanube.enums.TiendanubeInventoryStatus;
import com.rodrilang.librarymanager.integrations.tiendanube.event.TiendanubePublicationRequestedEvent;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountItem;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountSession;
import com.rodrilang.librarymanager.model.Inventory;
import com.rodrilang.librarymanager.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class InventoryCountProvisioningService {

    private final InventoryRepository inventoryRepository;
    private final InventoryCountPriceResolver priceResolver;
    private final ApplicationEventPublisher eventPublisher;

    public Inventory findOrCreate(InventoryCountItem item) {
        Inventory existing = priceResolver.existingInventory(item).orElse(null);
        if (existing != null) {
            applyExplicitOverrides(existing, item);
            if (!Boolean.TRUE.equals(existing.getActive())) {
                existing.setActive(true);
            }
            return inventoryRepository.save(existing);
        }

        InventoryCountSession session = item.getSession();
        BigDecimal editorialPrice = priceResolver.currentEditorialPrice(item.getBook()).orElse(null);
        boolean editorialSync = effectiveEditorialSync(item, null) && editorialPrice != null;
        BigDecimal salePrice = editorialSync
                ? editorialPrice
                : priceResolver.resolve(item).orElseThrow(
                        () -> new BusinessException("El libro necesita un precio de venta antes de aplicar la carga")
                );
        boolean publish = effectivePublish(item, null);

        Inventory created = inventoryRepository.save(Inventory.builder()
                .book(item.getBook())
                .bookstore(session.getBookstore())
                .condition(session.getCondition())
                .salePrice(salePrice)
                .stock(0)
                .minimumStock(effectiveMinimumStock(item, null))
                .tiendanubeStatus(publish
                        ? TiendanubeInventoryStatus.PENDING_PUBLICATION
                        : TiendanubeInventoryStatus.NOT_PUBLISHED)
                .tiendanubePriceSyncEnabled(effectiveTiendanubePriceSync(item, null))
                .editorialPriceSyncEnabled(editorialSync)
                .active(true)
                .build());

        if (publish) {
            eventPublisher.publishEvent(new TiendanubePublicationRequestedEvent(created.getId()));
        }
        return created;
    }

    private void applyExplicitOverrides(Inventory inventory, InventoryCountItem item) {
        BigDecimal editorialPrice = priceResolver.currentEditorialPrice(item.getBook()).orElse(null);

        if (item.getEditorialPriceSyncOverride() != null) {
            boolean enabled = item.getSession().getCondition() == BookCondition.NEW
                    && Boolean.TRUE.equals(item.getEditorialPriceSyncOverride())
                    && editorialPrice != null;
            inventory.setEditorialPriceSyncEnabled(enabled);
            if (enabled) {
                inventory.setSalePrice(editorialPrice);
            }
        }

        if (item.getSalePriceOverride() != null && !Boolean.TRUE.equals(inventory.getEditorialPriceSyncEnabled())) {
            inventory.setSalePrice(item.getSalePriceOverride());
        }
        if (item.getMinimumStockOverride() != null) {
            inventory.setMinimumStock(item.getMinimumStockOverride());
        }
        if (item.getTiendanubePriceSyncOverride() != null) {
            inventory.setTiendanubePriceSyncEnabled(item.getTiendanubePriceSyncOverride());
        }
        if (Boolean.TRUE.equals(item.getPublishOnTiendanubeOverride())
                && inventory.getTiendanubeStatus() == TiendanubeInventoryStatus.NOT_PUBLISHED) {
            inventory.setTiendanubeStatus(TiendanubeInventoryStatus.PENDING_PUBLICATION);
            eventPublisher.publishEvent(new TiendanubePublicationRequestedEvent(inventory.getId()));
        }
    }

    private boolean effectiveEditorialSync(InventoryCountItem item, Inventory existing) {
        if (item.getEditorialPriceSyncOverride() != null) return item.getEditorialPriceSyncOverride();
        if (existing != null) return Boolean.TRUE.equals(existing.getEditorialPriceSyncEnabled());
        return Boolean.TRUE.equals(item.getSession().getDefaultEditorialPriceSyncEnabled());
    }

    private boolean effectivePublish(InventoryCountItem item, Inventory existing) {
        if (item.getPublishOnTiendanubeOverride() != null) return item.getPublishOnTiendanubeOverride();
        if (existing != null) return existing.getTiendanubeStatus() != TiendanubeInventoryStatus.NOT_PUBLISHED;
        return Boolean.TRUE.equals(item.getSession().getDefaultPublishOnTiendanube());
    }

    private boolean effectiveTiendanubePriceSync(InventoryCountItem item, Inventory existing) {
        if (item.getTiendanubePriceSyncOverride() != null) return item.getTiendanubePriceSyncOverride();
        if (existing != null) return Boolean.TRUE.equals(existing.getTiendanubePriceSyncEnabled());
        return Boolean.TRUE.equals(item.getSession().getDefaultTiendanubePriceSyncEnabled());
    }

    private int effectiveMinimumStock(InventoryCountItem item, Inventory existing) {
        if (item.getMinimumStockOverride() != null) return item.getMinimumStockOverride();
        if (existing != null) return existing.getMinimumStock();
        return item.getSession().getDefaultMinimumStock();
    }
}
