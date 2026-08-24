package com.rodrilang.librarymanager.integrations.tiendanube.listener;

import com.rodrilang.librarymanager.integrations.tiendanube.event.TiendanubeSyncRequestedEvent;
import com.rodrilang.librarymanager.integrations.tiendanube.service.TiendanubeProductSyncService;
import com.rodrilang.librarymanager.integrations.tiendanube.service.TiendanubeVariantSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class TiendanubeSyncEventListener {

    private final TiendanubeProductSyncService productSyncService;
    private final TiendanubeVariantSyncService variantSyncService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(TiendanubeSyncRequestedEvent event) {

        try {
            switch (event.type()) {
                case STOCK -> variantSyncService.syncStock(event.inventoryId());

                case PRICE -> variantSyncService.syncPrice(event.inventoryId());

                case PUBLICATION -> productSyncService.syncPublication(event.inventoryId());
            }

        } catch (RuntimeException exception) {
            log.error(
                    "Error procesando sincronización con Tiendanube. inventoryId={}, type={}",
                    event.inventoryId(),
                    event.type(),
                    exception
            );
        }
    }
}