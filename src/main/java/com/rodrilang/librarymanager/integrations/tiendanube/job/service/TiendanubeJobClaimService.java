package com.rodrilang.librarymanager.integrations.tiendanube.job.service;

import com.rodrilang.librarymanager.integrations.tiendanube.job.config.TiendanubeJobProperties;
import com.rodrilang.librarymanager.integrations.tiendanube.job.dto.TiendanubeClaimedJob;
import com.rodrilang.librarymanager.integrations.tiendanube.job.entity.TiendanubeSyncJob;
import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobAttemptStatus;
import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobStatus;
import com.rodrilang.librarymanager.integrations.tiendanube.job.repository.TiendanubeSyncAttemptRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.job.repository.TiendanubeSyncJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TiendanubeJobClaimService {

    private static final String LEASE_EXPIRED_ERROR_TYPE = "LEASE_EXPIRED";
    private static final String LEASE_EXPIRED_ERROR_MESSAGE = "El lease del intento anterior venció antes de finalizar.";
    private static final String MAX_ATTEMPTS_EXHAUSTED_ERROR_TYPE = "MAX_ATTEMPTS_EXHAUSTED";
    private static final String MAX_ATTEMPTS_EXHAUSTED_ERROR_MESSAGE = "El job agotó la cantidad máxima de intentos.";

    private final TiendanubeSyncJobRepository jobRepository;
    private final TiendanubeSyncAttemptRepository attemptRepository;
    private final TiendanubeJobProperties properties;

    @Transactional
    public List<TiendanubeClaimedJob> claimNextBatch() {
        Instant now = Instant.now();
        List<Long> ids = jobRepository.findClaimableIds(now, properties.getBatchSize());

        if (ids.isEmpty()) {
            return List.of();
        }

        List<TiendanubeSyncJob> jobs = jobRepository.findAllByIds(ids).stream()
                .sorted(Comparator.comparingInt(job -> ids.indexOf(job.getId())))
                .toList();
        List<TiendanubeClaimedJob> claimed = new ArrayList<>(jobs.size());

        for (TiendanubeSyncJob job : jobs) {
            if (job.getStatus() == TiendanubeJobStatus.PROCESSING) {
                attemptRepository.markProcessingAttempts(
                        job.getId(),
                        TiendanubeJobAttemptStatus.STALE,
                        TiendanubeJobAttemptStatus.PROCESSING,
                        now,
                        LEASE_EXPIRED_ERROR_TYPE,
                        LEASE_EXPIRED_ERROR_MESSAGE
                );
            }

            if (job.getAttemptCount() >= job.getMaxAttempts()) {
                markExhausted(job, now);
                continue;
            }

            UUID processingToken = UUID.randomUUID();
            job.setStatus(TiendanubeJobStatus.PROCESSING);
            job.setProcessingStartedAt(now);
            job.setLeaseUntil(now.plus(properties.getLeaseDuration()));
            job.setProcessingToken(processingToken);
            claimed.add(new TiendanubeClaimedJob(job.getId(), processingToken));
        }

        return claimed;
    }

    private void markExhausted(TiendanubeSyncJob job, Instant now) {
        job.setStatus(TiendanubeJobStatus.FAILED);
        job.setCompletedAt(now);
        job.setProcessingStartedAt(null);
        job.setLeaseUntil(null);
        job.setProcessingToken(null);
        job.setLastErrorType(MAX_ATTEMPTS_EXHAUSTED_ERROR_TYPE);
        job.setLastErrorMessage(MAX_ATTEMPTS_EXHAUSTED_ERROR_MESSAGE);
    }
}
