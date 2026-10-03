package com.rodrilang.librarymanager.inventory.pricing.repository;

import com.rodrilang.librarymanager.inventory.pricing.model.InventoryPriceImportClassification;
import com.rodrilang.librarymanager.inventory.pricing.model.InventoryPriceImportItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface InventoryPriceImportItemRepository extends JpaRepository<InventoryPriceImportItem, Long> {
    @EntityGraph(attributePaths = {"inventory", "inventory.book", "inventory.book.authors", "inventory.book.publisher"})
    List<InventoryPriceImportItem> findAllByPriceImportIdOrderByRowNumberAsc(Long importId);

    @EntityGraph(attributePaths = {"inventory", "inventory.book", "inventory.book.authors", "inventory.book.publisher"})
    List<InventoryPriceImportItem> findAllByPriceImportIdAndIdIn(Long importId, Collection<Long> ids);

    @EntityGraph(attributePaths = {"inventory", "inventory.book", "inventory.book.authors", "inventory.book.publisher"})
    List<InventoryPriceImportItem> findAllByPriceImportIdAndInventoryIdOrderByRowNumberAsc(Long importId, Long inventoryId);

    long countByPriceImportIdAndClassification(Long importId, InventoryPriceImportClassification classification);

    void deleteAllByPriceImportId(Long importId);
}
