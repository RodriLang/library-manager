package com.rodrilang.librarymanager.inventory.consignment.repository;

import com.rodrilang.librarymanager.inventory.consignment.model.ConsignmentSettlementItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ConsignmentSettlementItemRepository extends JpaRepository<ConsignmentSettlementItem, Long> {
    boolean existsByInventoryMovementId(Long inventoryMovementId);
    List<ConsignmentSettlementItem> findAllBySettlementIdOrderByIdAsc(Long settlementId);
    List<ConsignmentSettlementItem> findAllByInventoryMovementIdIn(Collection<Long> movementIds);

    @Query("""
            SELECT CASE WHEN COUNT(i) > 0 THEN true ELSE false END
            FROM ConsignmentSettlementItem i
            WHERE i.inventoryMovement.referenceType = com.rodrilang.librarymanager.enums.InventoryMovementReferenceType.SALE
              AND i.inventoryMovement.referenceId = :saleId
            """)
    boolean existsSettledSale(@Param("saleId") String saleId);
}
