package com.rodrilang.librarymanager.shipping.shipment.repository;

import com.rodrilang.librarymanager.shipping.shipment.model.StoreShipment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Collection;
import java.time.Instant;

public interface StoreShipmentRepository extends JpaRepository<StoreShipment, Long> {
    List<StoreShipment> findAllByOrderIdAndBookstoreIdOrderByCreatedAtDesc(Long orderId, Long bookstoreId);
    Optional<StoreShipment> findByIdAndOrderIdAndBookstoreId(Long id, Long orderId, Long bookstoreId);
    Optional<StoreShipment> findFirstByOrderIdAndBookstoreIdAndStatusNotOrderByCreatedAtDesc(Long orderId, Long bookstoreId, com.rodrilang.librarymanager.shipping.shipment.model.ShipmentStatus status);
    @Query("""
            select s from StoreShipment s
            where s.status in :statuses
              and s.trackingNumber is not null
              and (s.trackingNextSyncAt is null or s.trackingNextSyncAt <= :now)
            order by coalesce(s.trackingLastSyncedAt, s.createdAt) asc
            """)
    List<StoreShipment> findDueForTrackingSync(@Param("statuses") Collection<com.rodrilang.librarymanager.shipping.shipment.model.ShipmentStatus> statuses,
                                               @Param("now") Instant now, org.springframework.data.domain.Pageable pageable);
}
