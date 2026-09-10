package com.rodrilang.librarymanager.integrations.tiendanube.webhook.service;

import com.rodrilang.librarymanager.integrations.tiendanube.webhook.dto.TiendanubeClaimedWebhookEvent;
import com.rodrilang.librarymanager.integrations.tiendanube.work.enums.TiendanubeWorkType;
import com.rodrilang.librarymanager.integrations.tiendanube.work.service.TiendanubeWorkSignal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TiendanubeWebhookScheduler {

    private static final Duration ERROR_RETRY_DELAY = Duration.ofSeconds(30);

    private final TiendanubeWebhookClaimService claimService;
    private final TiendanubeWebhookProcessor processor;
    private final TiendanubeWebhookWorkerExecutor workerExecutor;
    private final TiendanubeWorkSignal workSignal;

    @Scheduled(
            fixedDelayString = "${tiendanube.work.tick-delay-ms:1000}",
            initialDelayString = "${tiendanube.webhook-inbox.initial-delay-ms:3000}"
    )
    public void processPendingWebhooks() {
        if (!workSignal.shouldRun(TiendanubeWorkType.WEBHOOK)) {
            return;
        }

        try {
            List<TiendanubeClaimedWebhookEvent> events = claimService.claimDueEvents();

            for (TiendanubeClaimedWebhookEvent event : events) {
                workerExecutor.execute(() -> process(event));
            }

            refreshNextWake();
        } catch (RuntimeException exception) {
            workSignal.scheduleAt(TiendanubeWorkType.WEBHOOK, Instant.now().plus(ERROR_RETRY_DELAY));
            log.error("Could not claim Tiendanube webhooks. A retry was scheduled in memory", exception);
        }
    }

    private void refreshNextWake() {
        workSignal.replaceScheduledAt(TiendanubeWorkType.WEBHOOK, claimService.findNextWakeAt());
    }

    private void process(TiendanubeClaimedWebhookEvent event) {
        try {
            processor.process(event);
        } catch (RuntimeException exception) {
            log.error("Unexpected Tiendanube webhook processor error. eventId={}", event.id(), exception);
        }
    }
}
