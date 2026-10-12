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
import java.time.Instant;
import org.springframework.data.domain.PageRequest;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShipmentTrackingSyncJob {
    private final StoreShipmentRepository shipmentRepository;
    private final ShipmentTrackingService trackingService;

    @Scheduled(fixedDelayString = "${app.store.shipping.tracking-sync-ms:900000}")
    @Transactional
    public void syncActiveShipments() {
        List<StoreShipment> shipments = shipmentRepository.findDueForTrackingSync(
                List.of(ShipmentStatus.READY_TO_SHIP, ShipmentStatus.IN_TRANSIT, ShipmentStatus.OUT_FOR_DELIVERY, ShipmentStatus.EXCEPTION),
                Instant.now(), PageRequest.of(0, 100));
        for (StoreShipment shipment : shipments) {
            try {
                trackingService.refreshIfSupported(shipment);
            } catch (Exception ex) {
                trackingService.markSyncFailure(shipment);
                log.warn("No se pudo actualizar tracking shipmentId={} provider={} failures={}: {}", shipment.getId(), shipment.getProvider(), shipment.getTrackingSyncFailures(), ex.getMessage());
            }
        }
    }
}
