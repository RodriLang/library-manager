package com.rodrilang.librarymanager.inventory.count.listener;

import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobType;
import com.rodrilang.librarymanager.integrations.tiendanube.job.service.TiendanubeJobRequestService;
import com.rodrilang.librarymanager.inventory.count.event.InventoryCountStockChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class InventoryCountStockChangedListener {

    private final TiendanubeJobRequestService tiendanubeJobRequestService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStockChanged(InventoryCountStockChangedEvent event) {
        if (event.inventoryIds() == null || event.inventoryIds().isEmpty()) {
            return;
        }

        tiendanubeJobRequestService.enqueueAutomaticLinked(event.inventoryIds(), TiendanubeJobType.SYNC_STOCK);
    }
}
