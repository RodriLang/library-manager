package com.rodrilang.librarymanager.inventory.cost.service;

import com.rodrilang.librarymanager.inventory.cost.dto.response.InventoryCostLayerResponse;
import com.rodrilang.librarymanager.inventory.cost.model.InventoryCostLayer;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.model.Inventory;
import org.springframework.stereotype.Component;

@Component
public class InventoryCostResponseMapper {

    public InventoryCostLayerResponse toResponse(InventoryCostLayer layer) {
        Inventory inventory = layer.getInventory();
        Book book = inventory.getBook();

        return new InventoryCostLayerResponse(
                layer.getId(),
                inventory.getId(),
                book.getId(),
                book.getPreferredIsbn(),
                book.getTitle(),
                inventory.getCondition(),
                layer.getQuantityReceived(),
                layer.getQuantityRemaining(),
                layer.consumedQuantity(),
                layer.getCostType(),
                layer.getUnitCost(),
                layer.getDiscountPercentage(),
                layer.getReferencePrice(),
                layer.getReferencePriceSource(),
                layer.getSourceType(),
                layer.getSourceReferenceId(),
                layer.getEnteredAt(),
                layer.getCreatedAt(),
                layer.getUpdatedAt()
        );
    }
}
