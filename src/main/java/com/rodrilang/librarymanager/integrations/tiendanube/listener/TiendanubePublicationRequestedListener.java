package com.rodrilang.librarymanager.integrations.tiendanube.listener;

import com.rodrilang.librarymanager.integrations.tiendanube.event.TiendanubePublicationRequestedEvent;
import com.rodrilang.librarymanager.integrations.tiendanube.job.service.TiendanubeJobRequestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class TiendanubePublicationRequestedListener {

    private final TiendanubeJobRequestService jobRequestService;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void handle(TiendanubePublicationRequestedEvent event) {
        jobRequestService.enqueueAutomaticPublish(event.inventoryId())
                .ifPresent(jobId -> log.debug("Tiendanube publication job enqueued. jobId={} inventoryId={}",
                        jobId, event.inventoryId()));
    }
}
