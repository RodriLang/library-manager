package com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.service;

import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.dto.TiendanubeClaimedReconciliationRun;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "tiendanube.reconciliation", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class TiendanubeReconciliationScheduler {

    private final TiendanubeReconciliationRequestService requestService;
    private final TiendanubeReconciliationClaimService claimService;
    private final TiendanubeReconciliationProcessor processor;
    private final TiendanubeReconciliationWorkerExecutor workerExecutor;

    @Value("${tiendanube.reconciliation.claim-batch-size:4}")
    private int claimBatchSize;

    @Scheduled(
            fixedDelayString = "${tiendanube.reconciliation.worker-delay-ms:1000}",
            initialDelayString = "${tiendanube.reconciliation.worker-initial-delay-ms:5000}"
    )
    public void processPendingRuns() {
        List<TiendanubeClaimedReconciliationRun> runs = claimService.claim(Math.max(1, claimBatchSize));

        for (TiendanubeClaimedReconciliationRun run : runs) {
            workerExecutor.execute(() -> processor.process(run));
        }
    }

    @Scheduled(
            fixedDelayString = "${tiendanube.reconciliation.schedule-delay-ms:21600000}",
            initialDelayString = "${tiendanube.reconciliation.schedule-initial-delay-ms:300000}"
    )
    public void createScheduledRuns() {
        try {
            requestService.createScheduledRuns();
        } catch (RuntimeException exception) {
            log.error("Could not create scheduled Tiendanube reconciliation runs", exception);
        }
    }
}
