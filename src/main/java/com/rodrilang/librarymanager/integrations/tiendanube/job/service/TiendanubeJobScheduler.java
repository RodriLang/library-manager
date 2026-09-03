package com.rodrilang.librarymanager.integrations.tiendanube.job.service;

import com.rodrilang.librarymanager.integrations.tiendanube.job.dto.TiendanubeClaimedJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "tiendanube.jobs", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TiendanubeJobScheduler {

    private final TiendanubeJobClaimService claimService;
    private final TiendanubeJobProcessor processor;

    @Scheduled(
            fixedDelayString = "${tiendanube.jobs.poll-delay-ms:5000}",
            initialDelayString = "${tiendanube.jobs.initial-delay-ms:5000}"
    )
    public void processPendingJobs() {
        List<TiendanubeClaimedJob> jobs = claimService.claimNextBatch();

        for (TiendanubeClaimedJob job : jobs) {
            try {
                processor.process(job);
            } catch (RuntimeException exception) {
                log.error("Unexpected Tiendanube job processor error. jobId={}", job.jobId(), exception);
            }
        }
    }
}
