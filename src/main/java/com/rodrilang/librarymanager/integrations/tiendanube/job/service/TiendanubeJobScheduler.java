package com.rodrilang.librarymanager.integrations.tiendanube.job.service;

import com.rodrilang.librarymanager.integrations.tiendanube.job.dto.TiendanubeClaimedJob;
import com.rodrilang.librarymanager.integrations.tiendanube.work.enums.TiendanubeWorkType;
import com.rodrilang.librarymanager.integrations.tiendanube.work.service.TiendanubeWorkSignal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "tiendanube.jobs", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TiendanubeJobScheduler {

    private static final Duration ERROR_RETRY_DELAY = Duration.ofSeconds(30);

    private final TiendanubeJobClaimService claimService;
    private final TiendanubeJobProcessor processor;
    private final TiendanubeJobWorkerExecutor workerExecutor;
    private final TiendanubeWorkSignal workSignal;

    @Scheduled(
            fixedDelayString = "${tiendanube.work.tick-delay-ms:1000}",
            initialDelayString = "${tiendanube.jobs.initial-delay-ms:5000}"
    )
    public void processPendingJobs() {
        if (!workSignal.shouldRun(TiendanubeWorkType.JOB)) {
            return;
        }

        try {
            List<TiendanubeClaimedJob> jobs = claimService.claimNextBatch();

            for (TiendanubeClaimedJob job : jobs) {
                workerExecutor.execute(() -> process(job));
            }

            refreshNextWake();
        } catch (RuntimeException exception) {
            workSignal.scheduleAt(TiendanubeWorkType.JOB, Instant.now().plus(ERROR_RETRY_DELAY));
            log.error("Could not claim Tiendanube jobs. A retry was scheduled in memory", exception);
        }
    }

    private void refreshNextWake() {
        workSignal.replaceScheduledAt(TiendanubeWorkType.JOB, claimService.findNextWakeAt());
    }

    private void process(TiendanubeClaimedJob job) {
        try {
            processor.process(job);
        } catch (RuntimeException exception) {
            log.error("Unexpected Tiendanube job processor error. jobId={}", job.jobId(), exception);
        }
    }
}
