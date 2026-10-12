package com.rodrilang.librarymanager.shipping.shipment.repository;

import com.rodrilang.librarymanager.shipping.shipment.model.StoreShipmentTrackingEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StoreShipmentTrackingEventRepository extends JpaRepository<StoreShipmentTrackingEvent, Long> {
    List<StoreShipmentTrackingEvent> findAllByShipmentIdOrderByEventAtAscIdAsc(Long shipmentId);
    Optional<StoreShipmentTrackingEvent> findByShipmentIdAndProviderEventId(Long shipmentId, String providerEventId);
}
