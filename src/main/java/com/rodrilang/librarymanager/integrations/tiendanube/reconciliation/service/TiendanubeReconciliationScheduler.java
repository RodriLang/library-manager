package com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.service;

import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.dto.TiendanubeClaimedReconciliationRun;
import com.rodrilang.librarymanager.integrations.tiendanube.work.enums.TiendanubeWorkType;
import com.rodrilang.librarymanager.integrations.tiendanube.work.service.TiendanubeWorkSignal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "tiendanube.reconciliation", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class TiendanubeReconciliationScheduler {

    private static final Duration ERROR_RETRY_DELAY = Duration.ofSeconds(30);

    private final TiendanubeReconciliationRequestService requestService;
    private final TiendanubeReconciliationClaimService claimService;
    private final TiendanubeReconciliationProcessor processor;
    private final TiendanubeReconciliationWorkerExecutor workerExecutor;
    private final TiendanubeWorkSignal workSignal;

    @Value("${tiendanube.reconciliation.claim-batch-size:4}")
    private int claimBatchSize;

    @Scheduled(
            fixedDelayString = "${tiendanube.work.tick-delay-ms:1000}",
            initialDelayString = "${tiendanube.reconciliation.worker-initial-delay-ms:5000}"
    )
    public void processPendingRuns() {
        if (!workSignal.shouldRun(TiendanubeWorkType.RECONCILIATION)) {
            return;
        }

        try {
            List<TiendanubeClaimedReconciliationRun> runs = claimService.claim(Math.max(1, claimBatchSize));

            for (TiendanubeClaimedReconciliationRun run : runs) {
                workerExecutor.execute(() -> process(run));
            }

            refreshNextWake();
        } catch (RuntimeException exception) {
            workSignal.scheduleAt(TiendanubeWorkType.RECONCILIATION, Instant.now().plus(ERROR_RETRY_DELAY));
            log.error("Could not claim Tiendanube reconciliation runs. A retry was scheduled in memory", exception);
        }
    }

    @Scheduled(
            cron = "${tiendanube.reconciliation.cron:0 30 7,19 * * *}",
            zone = "${app.scheduling.zone:America/Argentina/Buenos_Aires}"
    )
    public void createScheduledRuns() {
        try {
            requestService.createScheduledRuns();
        } catch (RuntimeException exception) {
            log.error("Could not create scheduled Tiendanube reconciliation runs", exception);
        }
    }

    private void refreshNextWake() {
        workSignal.replaceScheduledAt(TiendanubeWorkType.RECONCILIATION, claimService.findNextWakeAt());
    }

    private void process(TiendanubeClaimedReconciliationRun run) {
        try {
            processor.process(run);
        } catch (RuntimeException exception) {
            log.error("Unexpected Tiendanube reconciliation processor error. runId={}", run.id(), exception);
        }
    }
}
