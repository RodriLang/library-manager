package com.rodrilang.librarymanager.shipping.shipment.repository;

import com.rodrilang.librarymanager.shipping.shipment.model.StoreShipment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.Collection;

public interface StoreShipmentRepository extends JpaRepository<StoreShipment, Long> {
    List<StoreShipment> findAllByOrderIdAndBookstoreIdOrderByCreatedAtDesc(Long orderId, Long bookstoreId);
    Optional<StoreShipment> findByIdAndOrderIdAndBookstoreId(Long id, Long orderId, Long bookstoreId);
    Optional<StoreShipment> findFirstByOrderIdAndBookstoreIdAndStatusNotOrderByCreatedAtDesc(Long orderId, Long bookstoreId, com.rodrilang.librarymanager.shipping.shipment.model.ShipmentStatus status);
    List<StoreShipment> findTop100ByStatusInAndTrackingNumberIsNotNullOrderByUpdatedAtAsc(Collection<com.rodrilang.librarymanager.shipping.shipment.model.ShipmentStatus> statuses);
}
