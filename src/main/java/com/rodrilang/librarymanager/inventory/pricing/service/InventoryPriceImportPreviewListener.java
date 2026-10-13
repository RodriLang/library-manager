package com.rodrilang.librarymanager.inventory.pricing.service;

import com.rodrilang.librarymanager.inventory.pricing.event.InventoryPriceImportPreviewRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryPriceImportPreviewListener {

    private final InventoryPriceImportPreviewWorker worker;
    private final InventoryPriceImportService importService;

    @Async("priceImportTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPreviewRequested(InventoryPriceImportPreviewRequestedEvent event) {
        try {
            worker.process(event.importId(), event.sourcePath());
        } catch (Exception exception) {
            log.error(
                    "Failed to generate inventory price import preview importId={}",
                    event.importId(),
                    exception
            );
            importService.markPreviewFailed(event.importId(), exception);
        }
    }
}
