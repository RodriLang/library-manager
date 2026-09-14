package com.rodrilang.librarymanager.inventory.count.service;

import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.integrations.tiendanube.enums.TiendanubeInventoryStatus;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountItem;
import com.rodrilang.librarymanager.model.Inventory;
import com.rodrilang.librarymanager.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class InventoryCountProvisioningService {

    private final InventoryRepository inventoryRepository;
    private final InventoryCountPriceResolver priceResolver;

    public Inventory findOrCreate(InventoryCountItem item) {
        Inventory existing = inventoryRepository.findByBookIdAndBookstoreIdAndCondition(
                item.getBook().getId(),
                item.getSession().getBookstore().getId(),
                item.getSession().getCondition()
        ).orElse(null);

        if (existing != null) {
            if (!Boolean.TRUE.equals(existing.getActive())) {
                existing.setActive(true);
            }
            return existing;
        }

        BigDecimal salePrice = priceResolver.resolve(item)
                .orElseThrow(() -> new BusinessException("El libro necesita un precio de venta antes de aplicar el conteo"));

        return inventoryRepository.save(Inventory.builder()
                .book(item.getBook())
                .bookstore(item.getSession().getBookstore())
                .condition(item.getSession().getCondition())
                .salePrice(salePrice)
                .stock(0)
                .minimumStock(0)
                .tiendanubeStatus(TiendanubeInventoryStatus.NOT_PUBLISHED)
                .tiendanubePriceSyncEnabled(false)
                .editorialPriceSyncEnabled(false)
                .active(true)
                .build());
    }
}
