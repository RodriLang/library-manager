package com.rodrilang.librarymanager.integrations.tiendanube.webhook.service;

import com.rodrilang.librarymanager.integrations.tiendanube.webhook.dto.TiendanubeClaimedWebhookEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TiendanubeWebhookScheduler {

    private final TiendanubeWebhookClaimService claimService;
    private final TiendanubeWebhookProcessor processor;
    private final TiendanubeWebhookWorkerExecutor workerExecutor;

    @Scheduled(
            fixedDelayString = "${tiendanube.webhook-inbox.poll-delay-ms:1000}",
            initialDelayString = "${tiendanube.webhook-inbox.initial-delay-ms:3000}"
    )
    public void processPendingWebhooks() {
        List<TiendanubeClaimedWebhookEvent> events = claimService.claimDueEvents();

        for (TiendanubeClaimedWebhookEvent event : events) {
            workerExecutor.execute(() -> process(event));
        }
    }

    private void process(TiendanubeClaimedWebhookEvent event) {
        try {
            processor.process(event);
        } catch (RuntimeException exception) {
            log.error("Unexpected Tiendanube webhook processor error. eventId={}", event.id(), exception);
        }
    }
}
