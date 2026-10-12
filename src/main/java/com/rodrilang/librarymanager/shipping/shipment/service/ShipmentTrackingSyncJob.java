package com.rodrilang.librarymanager.shipping.shipment.service;

import com.rodrilang.librarymanager.shipping.shipment.model.ShipmentStatus;
import com.rodrilang.librarymanager.shipping.shipment.model.StoreShipment;
import com.rodrilang.librarymanager.shipping.shipment.repository.StoreShipmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShipmentTrackingSyncJob {
    private final StoreShipmentRepository shipmentRepository;
    private final ShipmentTrackingService trackingService;

    @Scheduled(fixedDelayString = "${app.store.shipping.tracking-sync-ms:900000}")
    @Transactional
    public void syncActiveShipments() {
        List<StoreShipment> shipments = shipmentRepository.findTop100ByStatusInAndTrackingNumberIsNotNullOrderByUpdatedAtAsc(
                List.of(ShipmentStatus.READY_TO_SHIP, ShipmentStatus.IN_TRANSIT, ShipmentStatus.OUT_FOR_DELIVERY, ShipmentStatus.EXCEPTION));
        for (StoreShipment shipment : shipments) {
            try {
                trackingService.refreshIfSupported(shipment);
            } catch (Exception ex) {
                log.warn("No se pudo actualizar tracking shipmentId={} provider={}: {}", shipment.getId(), shipment.getProvider(), ex.getMessage());
            }
        }
    }
}
