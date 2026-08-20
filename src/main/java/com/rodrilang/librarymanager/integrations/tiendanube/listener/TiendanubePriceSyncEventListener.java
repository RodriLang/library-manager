package com.rodrilang.librarymanager.integrations.tiendanube.listener;

import com.rodrilang.librarymanager.integrations.tiendanube.event.TiendanubePriceSyncRequestedEvent;
import com.rodrilang.librarymanager.integrations.tiendanube.service.TiendanubeVariantSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class TiendanubePriceSyncEventListener {

    private final TiendanubeVariantSyncService variantSyncService;

    @Async("tiendanubePriceSyncExecutor")
    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    public void onPriceSyncRequested(
            TiendanubePriceSyncRequestedEvent event
    ) {
        log.info(
                "Tiendanube price sync started. inventories={}",
                event.inventoryIds().size()
        );

        int success = 0;
        int failed = 0;

        for (Long inventoryId : event.inventoryIds()) {
            try {
                variantSyncService.syncPrice(inventoryId);
                success++;
            } catch (Exception exception) {
                failed++;

                log.error(
                        "Tiendanube price sync failed. inventoryId={}",
                        inventoryId,
                        exception
                );
            }
        }

        log.info(
                "Tiendanube price sync completed. "
                        + "requested={} success={} failed={}",
                event.inventoryIds().size(),
                success,
                failed
        );
    }
}