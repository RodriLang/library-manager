package com.rodrilang.librarymanager.inventory.pricing.repository;

import com.rodrilang.librarymanager.inventory.pricing.model.InventoryPriceImportClassification;
import com.rodrilang.librarymanager.inventory.pricing.model.InventoryPriceImportItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @EntityGraph(attributePaths = {"inventory", "inventory.book"})
    @Query("""
            select item
            from InventoryPriceImportItem item
            where item.priceImport.id = :importId
              and item.selectedForApply = true
              and item.id > :afterId
            order by item.id asc
            """)
    List<InventoryPriceImportItem> findSelectedBatch(
            @Param("importId") Long importId,
            @Param("afterId") Long afterId,
            Pageable pageable
    );

    long countByPriceImportIdAndDiscardedFalseAndSelectedForApplyFalse(Long importId);
    long countByPriceImportIdAndDiscardedFalse(Long importId);
    long countByPriceImportIdAndAppliedTrue(Long importId);

    void deleteAllByPriceImportId(Long importId);
}
