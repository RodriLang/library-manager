package com.rodrilang.librarymanager.shipping.shipment.repository;

import com.rodrilang.librarymanager.shipping.shipment.model.StoreShipmentItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface StoreShipmentItemRepository extends JpaRepository<StoreShipmentItem, Long> {
    List<StoreShipmentItem> findAllByShipmentIdOrderByIdAsc(Long shipmentId);

    @Query("""
        select coalesce(sum(si.quantity), 0)
        from StoreShipmentItem si
        where si.orderItem.id = :orderItemId
          and si.shipment.status <> com.rodrilang.librarymanager.shipping.shipment.model.ShipmentStatus.CANCELLED
    """)
    Long sumAllocatedQuantity(@Param("orderItemId") Long orderItemId);
}
