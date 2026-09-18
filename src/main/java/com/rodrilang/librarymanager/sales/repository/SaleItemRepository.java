package com.rodrilang.librarymanager.sales.repository;

import com.rodrilang.librarymanager.sales.model.SaleItem;
import com.rodrilang.librarymanager.sales.repository.projection.SaleItemsSummaryProjection;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface SaleItemRepository extends JpaRepository<SaleItem, Long> {

    @EntityGraph(attributePaths = {
            "inventory",
            "inventory.book"
    })
    List<SaleItem> findAllBySaleIdOrderByIdAsc(Long saleId);

    @Query("""
            SELECT
                item.sale.id AS saleId,
                COUNT(item.id) AS itemCount,
                COALESCE(SUM(item.quantity), 0) AS totalUnits
            FROM SaleItem item
            WHERE item.sale.id IN :saleIds
            GROUP BY item.sale.id
            """)
    List<SaleItemsSummaryProjection> findSummariesBySaleIds(
            @Param("saleIds") Collection<Long> saleIds
    );
}
