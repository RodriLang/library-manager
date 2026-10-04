package com.rodrilang.librarymanager.inventory.pricing.service;

import com.rodrilang.librarymanager.inventory.pricing.event.InventoryPriceImportApplyRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryPriceImportApplyListener {

    private final InventoryPriceImportApplyWorker worker;
    private final InventoryPriceImportApplyService applyService;

    @Async("priceImportTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onApplyRequested(InventoryPriceImportApplyRequestedEvent event) {
        try {
            worker.process(event.importId(), event.userId());
        } catch (Exception exception) {
            log.error(
                    "Failed to apply inventory price import importId={}",
                    event.importId(),
                    exception
            );
            applyService.markFailed(event.importId(), exception);
        }
    }
}
