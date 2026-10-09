package com.rodrilang.librarymanager.store.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StoreReservationCleanupJob {
    private final StoreOrderService service;

    @Scheduled(fixedDelayString = "${app.store.reservations.cleanup-ms:60000}")
    public void releaseExpiredReservations() {
        service.expirePendingOrders();
    }
}
