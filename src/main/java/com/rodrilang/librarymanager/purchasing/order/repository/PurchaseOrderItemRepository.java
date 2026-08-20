package com.rodrilang.librarymanager.purchasing.order.repository;

import com.rodrilang.librarymanager.purchasing.order.model.PurchaseOrderItem;
import com.rodrilang.librarymanager.purchasing.order.repository.projection.PurchaseOrderTotalsProjection;
import com.rodrilang.librarymanager.purchasing.order.repository.projection.PurchaseRequirementOrderedQuantityProjection;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PurchaseOrderItemRepository
        extends JpaRepository<PurchaseOrderItem, Long> {

    @EntityGraph(attributePaths = {
            "book",
            "requirement"
    })
    List<PurchaseOrderItem>
    findAllByPurchaseOrderIdOrderByIdAsc(
            Long purchaseOrderId
    );

    Optional<PurchaseOrderItem>
    findByIdAndPurchaseOrderId(
            Long itemId,
            Long purchaseOrderId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT item
            FROM PurchaseOrderItem item
            WHERE item.purchaseOrder.id = :purchaseOrderId
              AND item.book.id = :bookId
            """)
    Optional<PurchaseOrderItem> findByPurchaseOrderIdAndBookIdForUpdate(
            @Param("purchaseOrderId") Long purchaseOrderId,
            @Param("bookId") Long bookId
    );

    @Query("""
            SELECT COALESCE(SUM(item.requirementQuantity), 0)
            FROM PurchaseOrderItem item
            WHERE item.requirement.id = :requirementId
              AND item.purchaseOrder.status <>
                  com.rodrilang.librarymanager.purchasing.order.model.PurchaseOrderStatus.CANCELLED
            """)
    Long sumOrderedQuantityByRequirementId(
            @Param("requirementId") Long requirementId
    );

    @Query("""
            SELECT
                item.purchaseOrder.id AS orderId,
                COUNT(item.id) AS itemCount,
                COALESCE(SUM(item.quantity), 0) AS totalUnits,
                SUM(
                    CASE
                        WHEN item.unitPrice IS NOT NULL
                        THEN item.unitPrice * item.quantity
                        ELSE null
                    END
                ) AS estimatedTotal
            FROM PurchaseOrderItem item
            WHERE item.purchaseOrder.id IN :orderIds
            GROUP BY item.purchaseOrder.id
            """)
    List<PurchaseOrderTotalsProjection> findTotalsByOrderIds(
            @Param("orderIds")
            Collection<Long> orderIds
    );

    @Query("""
            SELECT
                item.requirement.id AS requirementId,
                COALESCE(SUM(item.requirementQuantity), 0)
                    AS orderedQuantity
            FROM PurchaseOrderItem item
            WHERE item.requirement.id IN :requirementIds
              AND item.purchaseOrder.status <>
                  com.rodrilang.librarymanager.purchasing.order.model.PurchaseOrderStatus.CANCELLED
            GROUP BY item.requirement.id
            """)
    List<PurchaseRequirementOrderedQuantityProjection>
    findOrderedQuantitiesByRequirementIds(
            @Param("requirementIds")
            Collection<Long> requirementIds
    );
}