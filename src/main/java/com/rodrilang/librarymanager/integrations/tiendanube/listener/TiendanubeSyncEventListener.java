package com.rodrilang.librarymanager.integrations.tiendanube.listener;

import com.rodrilang.librarymanager.integrations.tiendanube.event.TiendanubeSyncRequestedEvent;
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
public class TiendanubeSyncEventListener {

    private final TiendanubeJobRequestService jobRequestService;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void handle(TiendanubeSyncRequestedEvent event) {
        TiendanubeJobType jobType = switch (event.type()) {
            case STOCK -> TiendanubeJobType.SYNC_STOCK;
            case PRICE -> TiendanubeJobType.SYNC_PRICE;
            case PUBLICATION -> TiendanubeJobType.SYNC_PUBLICATION;
        };

        jobRequestService.enqueueAutomaticLinked(event.inventoryId(), jobType)
                .ifPresent(jobId -> log.debug("Tiendanube job enqueued. jobId={} inventoryId={} type={}",
                        jobId, event.inventoryId(), jobType));
    }
}
