package com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.service;

import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.model.TiendanubeClaimedImportAnalysisRun;
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
@ConditionalOnProperty(
        prefix = "tiendanube.import-analysis",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
@RequiredArgsConstructor
public class TiendanubeImportAnalysisScheduler {

    private static final Duration ERROR_RETRY_DELAY = Duration.ofSeconds(30);

    private final TiendanubeImportAnalysisClaimService claimService;
    private final TiendanubeImportAnalysisProcessor processor;
    private final TiendanubeImportAnalysisWorkerExecutor workerExecutor;
    private final TiendanubeWorkSignal workSignal;

    @Value("${tiendanube.import-analysis.claim-batch-size:1}")
    private int claimBatchSize;

    @Scheduled(
            fixedDelayString = "${tiendanube.work.tick-delay-ms:1000}",
            initialDelayString = "${tiendanube.import-analysis.worker-initial-delay-ms:5000}"
    )
    public void processPendingRuns() {
        if (!workSignal.shouldRun(TiendanubeWorkType.IMPORT_ANALYSIS)) {
            return;
        }

        try {
            List<TiendanubeClaimedImportAnalysisRun> runs = claimService.claim(Math.max(1, claimBatchSize));

            for (TiendanubeClaimedImportAnalysisRun run : runs) {
                workerExecutor.execute(() -> process(run));
            }

            refreshNextWake();
        } catch (RuntimeException exception) {
            workSignal.scheduleAt(
                    TiendanubeWorkType.IMPORT_ANALYSIS,
                    Instant.now().plus(ERROR_RETRY_DELAY)
            );
            log.error("Could not claim Tiendanube import analysis runs", exception);
        }
    }

    private void refreshNextWake() {
        workSignal.replaceScheduledAt(
                TiendanubeWorkType.IMPORT_ANALYSIS,
                claimService.findNextWakeAt()
        );
    }

    private void process(TiendanubeClaimedImportAnalysisRun run) {
        try {
            processor.process(run);
        } catch (RuntimeException exception) {
            log.error("Unexpected Tiendanube import analysis processor error. runId={}", run.id(), exception);
        }
    }
}
