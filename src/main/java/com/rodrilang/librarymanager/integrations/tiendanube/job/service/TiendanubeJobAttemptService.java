package com.rodrilang.librarymanager.integrations.tiendanube.job.service;

import com.rodrilang.librarymanager.integrations.tiendanube.job.dto.TiendanubeJobExecutionContext;
import com.rodrilang.librarymanager.integrations.tiendanube.job.entity.TiendanubeSyncAttempt;
import com.rodrilang.librarymanager.integrations.tiendanube.job.entity.TiendanubeSyncJob;
import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobAttemptStatus;
import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobStatus;
import com.rodrilang.librarymanager.integrations.tiendanube.job.repository.TiendanubeSyncAttemptRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.job.repository.TiendanubeSyncJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TiendanubeJobAttemptService {

    private final TiendanubeSyncJobRepository jobRepository;
    private final TiendanubeSyncAttemptRepository attemptRepository;

    @Transactional
    public Optional<TiendanubeJobExecutionContext> startAttempt(Long jobId, UUID processingToken) {
        TiendanubeSyncJob job = jobRepository.findByIdForUpdate(jobId).orElse(null);

        if (!ownsProcessingLease(job, processingToken) || job.getAttemptCount() >= job.getMaxAttempts()) {
            return Optional.empty();
        }

        int attemptNumber = job.getAttemptCount() + 1;
        job.setAttemptCount(attemptNumber);

        TiendanubeSyncAttempt attempt = attemptRepository.save(TiendanubeSyncAttempt.builder()
                .job(job)
                .attemptNumber(attemptNumber)
                .processingToken(processingToken)
                .status(TiendanubeJobAttemptStatus.PROCESSING)
                .startedAt(Instant.now())
                .build());

        return Optional.of(new TiendanubeJobExecutionContext(
                job.getId(),
                attempt.getId(),
                attemptNumber,
                job.getMaxAttempts(),
                job.getBookstoreId(),
                job.getStoreId(),
                job.getInventoryId(),
                job.getType(),
                job.getSource(),
                processingToken
        ));
    }

    private boolean ownsProcessingLease(TiendanubeSyncJob job, UUID processingToken) {
        return job != null
                && job.getStatus() == TiendanubeJobStatus.PROCESSING
                && processingToken.equals(job.getProcessingToken());
    }
}
