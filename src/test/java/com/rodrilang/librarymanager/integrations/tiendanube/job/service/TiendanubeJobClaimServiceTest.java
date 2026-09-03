package com.rodrilang.librarymanager.integrations.tiendanube.job.service;

import com.rodrilang.librarymanager.integrations.tiendanube.job.config.TiendanubeJobProperties;
import com.rodrilang.librarymanager.integrations.tiendanube.job.dto.TiendanubeClaimedJob;
import com.rodrilang.librarymanager.integrations.tiendanube.job.entity.TiendanubeSyncJob;
import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobAttemptStatus;
import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobSource;
import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobStatus;
import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobType;
import com.rodrilang.librarymanager.integrations.tiendanube.job.repository.TiendanubeSyncAttemptRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.job.repository.TiendanubeSyncJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TiendanubeJobClaimServiceTest {

    @Mock
    private TiendanubeSyncJobRepository jobRepository;

    @Mock
    private TiendanubeSyncAttemptRepository attemptRepository;

    private TiendanubeJobClaimService service;

    @BeforeEach
    void setUp() {
        TiendanubeJobProperties properties = new TiendanubeJobProperties();
        properties.setBatchSize(5);
        properties.setLeaseDuration(Duration.ofMinutes(10));
        service = new TiendanubeJobClaimService(jobRepository, attemptRepository, properties);
    }

    @Test
    void claimsPendingJobWithLeaseAndToken() {
        TiendanubeSyncJob job = job(TiendanubeJobStatus.PENDING, 0, 7);
        when(jobRepository.findClaimableIds(any(), eq(5))).thenReturn(List.of(1L));
        when(jobRepository.findAllByIds(List.of(1L))).thenReturn(List.of(job));

        List<TiendanubeClaimedJob> claimed = service.claimNextBatch();

        assertEquals(1, claimed.size());
        assertEquals(TiendanubeJobStatus.PROCESSING, job.getStatus());
        assertNotNull(job.getProcessingToken());
        assertNotNull(job.getLeaseUntil());
        assertNotNull(job.getProcessingStartedAt());
    }

    @Test
    void marksOpenAttemptStaleWhenExpiredLeaseIsReclaimed() {
        TiendanubeSyncJob job = job(TiendanubeJobStatus.PROCESSING, 1, 7);
        job.setLeaseUntil(Instant.now().minusSeconds(1));
        when(jobRepository.findClaimableIds(any(), eq(5))).thenReturn(List.of(1L));
        when(jobRepository.findAllByIds(List.of(1L))).thenReturn(List.of(job));

        service.claimNextBatch();

        verify(attemptRepository).markProcessingAttempts(
                eq(1L), eq(TiendanubeJobAttemptStatus.STALE), eq(TiendanubeJobAttemptStatus.PROCESSING),
                any(), eq("LEASE_EXPIRED"), any()
        );
    }

    @Test
    void exhaustedExpiredJobBecomesFailedInsteadOfBeingReclaimed() {
        TiendanubeSyncJob job = job(TiendanubeJobStatus.PROCESSING, 7, 7);
        job.setProcessingStartedAt(Instant.now().minusSeconds(30));
        job.setLeaseUntil(Instant.now().minusSeconds(1));
        when(jobRepository.findClaimableIds(any(), eq(5))).thenReturn(List.of(1L));
        when(jobRepository.findAllByIds(List.of(1L))).thenReturn(List.of(job));

        List<TiendanubeClaimedJob> claimed = service.claimNextBatch();

        assertEquals(0, claimed.size());
        assertEquals(TiendanubeJobStatus.FAILED, job.getStatus());
        assertNotNull(job.getCompletedAt());
        assertNull(job.getProcessingToken());
        assertNull(job.getLeaseUntil());
    }

    private TiendanubeSyncJob job(TiendanubeJobStatus status, int attempts, int maxAttempts) {
        return TiendanubeSyncJob.builder()
                .id(1L)
                .bookstoreId(2L)
                .storeId(3L)
                .inventoryId(4L)
                .type(TiendanubeJobType.SYNC_STOCK)
                .source(TiendanubeJobSource.AUTOMATIC)
                .status(status)
                .attemptCount(attempts)
                .maxAttempts(maxAttempts)
                .nextAttemptAt(Instant.now())
                .processingToken(status == TiendanubeJobStatus.PROCESSING ? java.util.UUID.randomUUID() : null)
                .processingStartedAt(status == TiendanubeJobStatus.PROCESSING ? Instant.now().minusSeconds(30) : null)
                .build();
    }
}
