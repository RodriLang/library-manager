package com.rodrilang.librarymanager.integrations.tiendanube.listener;

import com.rodrilang.librarymanager.integrations.tiendanube.event.TiendanubePriceSyncRequestedEvent;
import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobType;
import com.rodrilang.librarymanager.integrations.tiendanube.job.service.TiendanubeJobRequestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class TiendanubePriceSyncEventListener {

    private final TiendanubeJobRequestService jobRequestService;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void onPriceSyncRequested(TiendanubePriceSyncRequestedEvent event) {
        int requested = event.inventoryIds() == null ? 0 : event.inventoryIds().size();
        int enqueued = jobRequestService.enqueueAutomaticLinked(event.inventoryIds(), TiendanubeJobType.SYNC_PRICE).size();

        log.info("Tiendanube price sync jobs enqueued. requested={} enqueued={}", requested, enqueued);
    }
}
