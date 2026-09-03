package com.rodrilang.librarymanager.integrations.tiendanube.job.service;

import com.rodrilang.librarymanager.integrations.tiendanube.job.dto.TiendanubeJobExecutionContext;
import com.rodrilang.librarymanager.integrations.tiendanube.job.dto.TiendanubeJobFailure;
import com.rodrilang.librarymanager.integrations.tiendanube.job.entity.TiendanubeSyncAttempt;
import com.rodrilang.librarymanager.integrations.tiendanube.job.entity.TiendanubeSyncJob;
import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobAttemptStatus;
import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobFailureDisposition;
import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobSource;
import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobStatus;
import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobType;
import com.rodrilang.librarymanager.integrations.tiendanube.job.repository.TiendanubeSyncAttemptRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.job.repository.TiendanubeSyncJobRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TiendanubeJobCompletionServiceTest {

    @Mock
    private TiendanubeSyncJobRepository jobRepository;

    @Mock
    private TiendanubeSyncAttemptRepository attemptRepository;

    private final TiendanubeJobRetryPolicy retryPolicy = new TiendanubeJobRetryPolicy();

    @Test
    void completesJobAndClearsLease() {
        Fixture fixture = fixture(1, 7);
        TiendanubeJobCompletionService service = service(fixture);

        service.complete(fixture.context());

        assertEquals(TiendanubeJobStatus.COMPLETED, fixture.job().getStatus());
        assertEquals(TiendanubeJobAttemptStatus.COMPLETED, fixture.attempt().getStatus());
        assertNotNull(fixture.job().getCompletedAt());
        assertNull(fixture.job().getProcessingToken());
        assertNull(fixture.job().getLeaseUntil());
    }

    @Test
    void schedulesRetryWhenFailureIsRetryable() {
        Fixture fixture = fixture(1, 7);
        TiendanubeJobCompletionService service = service(fixture);
        TiendanubeJobFailure failure = new TiendanubeJobFailure(
                "TIMEOUT", "Timeout", null, TiendanubeJobFailureDisposition.RETRY
        );

        service.fail(fixture.context(), failure);

        assertEquals(TiendanubeJobStatus.RETRY_WAIT, fixture.job().getStatus());
        assertEquals(TiendanubeJobAttemptStatus.RETRY_SCHEDULED, fixture.attempt().getStatus());
        assertNotNull(fixture.job().getNextAttemptAt());
        assertEquals("TIMEOUT", fixture.job().getLastErrorType());
    }

    @Test
    void blocksJobWhenFailureRequiresReconnect() {
        Fixture fixture = fixture(1, 7);
        TiendanubeJobCompletionService service = service(fixture);
        TiendanubeJobFailure failure = new TiendanubeJobFailure(
                "UNAUTHORIZED", "Unauthorized", 401, TiendanubeJobFailureDisposition.BLOCK
        );

        service.fail(fixture.context(), failure);

        assertEquals(TiendanubeJobStatus.BLOCKED, fixture.job().getStatus());
        assertEquals(TiendanubeJobAttemptStatus.BLOCKED, fixture.attempt().getStatus());
        assertEquals(401, fixture.job().getLastHttpStatus());
    }

    @Test
    void staleWorkerCannotCompleteReclaimedJob() {
        Fixture fixture = fixture(1, 7);
        fixture.job().setProcessingToken(UUID.randomUUID());
        TiendanubeJobCompletionService service = service(fixture);

        service.complete(fixture.context());

        assertEquals(TiendanubeJobStatus.PROCESSING, fixture.job().getStatus());
        assertEquals(TiendanubeJobAttemptStatus.STALE, fixture.attempt().getStatus());
    }

    private TiendanubeJobCompletionService service(Fixture fixture) {
        when(jobRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(fixture.job()));
        when(attemptRepository.findById(10L)).thenReturn(Optional.of(fixture.attempt()));
        return new TiendanubeJobCompletionService(jobRepository, attemptRepository, retryPolicy);
    }

    private Fixture fixture(int attemptNumber, int maxAttempts) {
        UUID token = UUID.randomUUID();
        TiendanubeSyncJob job = TiendanubeSyncJob.builder()
                .id(1L)
                .bookstoreId(2L)
                .storeId(3L)
                .inventoryId(4L)
                .type(TiendanubeJobType.SYNC_STOCK)
                .source(TiendanubeJobSource.AUTOMATIC)
                .status(TiendanubeJobStatus.PROCESSING)
                .attemptCount(attemptNumber)
                .maxAttempts(maxAttempts)
                .nextAttemptAt(Instant.now())
                .processingStartedAt(Instant.now())
                .leaseUntil(Instant.now().plusSeconds(60))
                .processingToken(token)
                .build();
        TiendanubeSyncAttempt attempt = TiendanubeSyncAttempt.builder()
                .id(10L)
                .job(job)
                .attemptNumber(attemptNumber)
                .processingToken(token)
                .status(TiendanubeJobAttemptStatus.PROCESSING)
                .startedAt(Instant.now())
                .build();
        TiendanubeJobExecutionContext context = new TiendanubeJobExecutionContext(
                1L, 10L, attemptNumber, maxAttempts, 2L, 3L, 4L,
                TiendanubeJobType.SYNC_STOCK, TiendanubeJobSource.AUTOMATIC, token
        );

        return new Fixture(job, attempt, context);
    }

    private record Fixture(TiendanubeSyncJob job, TiendanubeSyncAttempt attempt, TiendanubeJobExecutionContext context) {
    }
}
