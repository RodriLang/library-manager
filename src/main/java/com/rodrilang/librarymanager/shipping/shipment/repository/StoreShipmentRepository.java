package com.rodrilang.librarymanager.shipping.shipment.repository;

import com.rodrilang.librarymanager.shipping.shipment.model.StoreShipment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StoreShipmentRepository extends JpaRepository<StoreShipment, Long> {
    List<StoreShipment> findAllByOrderIdAndBookstoreIdOrderByCreatedAtDesc(Long orderId, Long bookstoreId);
    Optional<StoreShipment> findByIdAndOrderIdAndBookstoreId(Long id, Long orderId, Long bookstoreId);
}
