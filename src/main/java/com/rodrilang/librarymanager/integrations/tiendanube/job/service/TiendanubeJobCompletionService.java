package com.rodrilang.librarymanager.integrations.tiendanube.job.service;

import com.rodrilang.librarymanager.integrations.tiendanube.job.dto.TiendanubeJobExecutionContext;
import com.rodrilang.librarymanager.integrations.tiendanube.job.dto.TiendanubeJobFailure;
import com.rodrilang.librarymanager.integrations.tiendanube.job.entity.TiendanubeSyncAttempt;
import com.rodrilang.librarymanager.integrations.tiendanube.job.entity.TiendanubeSyncJob;
import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobAttemptStatus;
import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobFailureDisposition;
import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobStatus;
import com.rodrilang.librarymanager.integrations.tiendanube.job.repository.TiendanubeSyncAttemptRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.job.repository.TiendanubeSyncJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class TiendanubeJobCompletionService {

    private static final String STALE_ATTEMPT_ERROR_TYPE = "STALE_ATTEMPT";
    private static final String STALE_ATTEMPT_ERROR_MESSAGE = "El job ya fue reclamado por otro worker.";
    private static final String SUPERSEDED_ERROR_TYPE = "SUPERSEDED_BY_PENDING_JOB";

    private final TiendanubeSyncJobRepository jobRepository;
    private final TiendanubeSyncAttemptRepository attemptRepository;
    private final TiendanubeJobRetryPolicy retryPolicy;

    @Transactional
    public void complete(TiendanubeJobExecutionContext context) {
        Instant now = Instant.now();
        TiendanubeSyncJob job = jobRepository.findByIdForUpdate(context.jobId()).orElse(null);
        TiendanubeSyncAttempt attempt = attemptRepository.findById(context.attemptId()).orElse(null);

        if (!ownsProcessingLease(job, context) || attempt == null) {
            markAttemptStale(attempt, now);
            return;
        }

        attempt.setStatus(TiendanubeJobAttemptStatus.COMPLETED);
        attempt.setCompletedAt(now);
        clearAttemptError(attempt);

        job.setStatus(TiendanubeJobStatus.COMPLETED);
        job.setCompletedAt(now);
        clearProcessingLease(job);
        clearJobError(job);
    }

    @Transactional
    public void fail(TiendanubeJobExecutionContext context, TiendanubeJobFailure failure) {
        Instant now = Instant.now();
        TiendanubeSyncJob job = jobRepository.findByIdForUpdate(context.jobId()).orElse(null);
        TiendanubeSyncAttempt attempt = attemptRepository.findById(context.attemptId()).orElse(null);

        if (!ownsProcessingLease(job, context) || attempt == null) {
            markAttemptStale(attempt, now);
            return;
        }

        registerAttemptFailure(attempt, failure, now);
        registerJobFailure(job, failure);
        clearProcessingLease(job);

        if (failure.disposition() == TiendanubeJobFailureDisposition.BLOCK) {
            job.setStatus(TiendanubeJobStatus.BLOCKED);
            attempt.setStatus(TiendanubeJobAttemptStatus.BLOCKED);
            return;
        }

        if (retryPolicy.shouldRetry(failure, context.attemptNumber(), context.maxAttempts())) {
            if (jobRepository.existsPendingSuccessor(context.inventoryId(), context.type().name(), context.jobId())) {
                job.setStatus(TiendanubeJobStatus.CANCELLED);
                job.setCompletedAt(now);
                job.setLastErrorType(SUPERSEDED_ERROR_TYPE);
                job.setLastErrorMessage("Un job pendiente más nuevo reemplazó este reintento. Último error: " + failure.message());
                attempt.setStatus(TiendanubeJobAttemptStatus.FAILED);

                log.info(
                        "Tiendanube job retry omitted because a newer pending job supersedes it. jobId={} type={} inventoryId={}",
                        context.jobId(), context.type(), context.inventoryId()
                );
                return;
            }

            job.setStatus(TiendanubeJobStatus.RETRY_WAIT);
            job.setNextAttemptAt(now.plus(retryPolicy.nextDelay(failure, context.attemptNumber())));
            attempt.setStatus(TiendanubeJobAttemptStatus.RETRY_SCHEDULED);
            return;
        }

        job.setStatus(TiendanubeJobStatus.FAILED);
        job.setCompletedAt(now);
        attempt.setStatus(TiendanubeJobAttemptStatus.FAILED);
    }

    private boolean ownsProcessingLease(TiendanubeSyncJob job, TiendanubeJobExecutionContext context) {
        return job != null
                && job.getStatus() == TiendanubeJobStatus.PROCESSING
                && context.processingToken().equals(job.getProcessingToken());
    }

    private void markAttemptStale(TiendanubeSyncAttempt attempt, Instant now) {
        if (attempt == null || attempt.getStatus() != TiendanubeJobAttemptStatus.PROCESSING) {
            return;
        }

        attempt.setStatus(TiendanubeJobAttemptStatus.STALE);
        attempt.setCompletedAt(now);
        attempt.setErrorType(STALE_ATTEMPT_ERROR_TYPE);
        attempt.setErrorMessage(STALE_ATTEMPT_ERROR_MESSAGE);
    }

    private void registerAttemptFailure(TiendanubeSyncAttempt attempt, TiendanubeJobFailure failure, Instant now) {
        attempt.setCompletedAt(now);
        attempt.setErrorType(failure.errorType());
        attempt.setErrorMessage(failure.message());
        attempt.setHttpStatus(failure.httpStatus());
    }

    private void registerJobFailure(TiendanubeSyncJob job, TiendanubeJobFailure failure) {
        job.setLastErrorType(failure.errorType());
        job.setLastErrorMessage(failure.message());
        job.setLastHttpStatus(failure.httpStatus());
    }

    private void clearProcessingLease(TiendanubeSyncJob job) {
        job.setProcessingStartedAt(null);
        job.setLeaseUntil(null);
        job.setProcessingToken(null);
    }

    private void clearJobError(TiendanubeSyncJob job) {
        job.setLastErrorType(null);
        job.setLastErrorMessage(null);
        job.setLastHttpStatus(null);
    }

    private void clearAttemptError(TiendanubeSyncAttempt attempt) {
        attempt.setErrorType(null);
        attempt.setErrorMessage(null);
        attempt.setHttpStatus(null);
    }
}
