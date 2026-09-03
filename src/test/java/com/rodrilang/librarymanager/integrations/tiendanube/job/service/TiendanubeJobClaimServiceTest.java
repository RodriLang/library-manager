package com.rodrilang.librarymanager.integrations.tiendanube.job.service;

import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeStore;
import com.rodrilang.librarymanager.integrations.tiendanube.job.config.TiendanubeJobProperties;
import com.rodrilang.librarymanager.integrations.tiendanube.job.dto.TiendanubeClaimedJob;
import com.rodrilang.librarymanager.integrations.tiendanube.job.entity.TiendanubeSyncJob;
import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobAttemptStatus;
import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobSource;
import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobStatus;
import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobType;
import com.rodrilang.librarymanager.integrations.tiendanube.job.repository.TiendanubeSyncAttemptRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.job.repository.TiendanubeSyncJobRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeStoreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

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

    @Mock
    private TiendanubeStoreRepository storeRepository;

    private TiendanubeJobClaimService service;

    @BeforeEach
    void setUp() {
        TiendanubeJobProperties properties = new TiendanubeJobProperties();
        properties.setBatchSize(5);
        properties.setLeaseDuration(Duration.ofMinutes(10));
        service = new TiendanubeJobClaimService(jobRepository, attemptRepository, storeRepository, properties);
    }

    @Test
    void claimsPendingJobWithLeaseAndToken() {
        TiendanubeSyncJob job = job(1L, 30L, TiendanubeJobStatus.PENDING, 0, 7);
        prepareClaim(job);

        List<TiendanubeClaimedJob> claimed = service.claimNextBatch();

        assertEquals(1, claimed.size());
        assertEquals(TiendanubeJobStatus.PROCESSING, job.getStatus());
        assertNotNull(job.getProcessingToken());
        assertNotNull(job.getLeaseUntil());
        assertNotNull(job.getProcessingStartedAt());
    }

    @Test
    void claimsAtMostOneJobPerStoreInSameBatch() {
        TiendanubeSyncJob first = job(1L, 30L, TiendanubeJobStatus.PENDING, 0, 7);
        TiendanubeSyncJob second = job(2L, 30L, TiendanubeJobStatus.PENDING, 0, 7);
        when(jobRepository.findClaimableIds(any(), eq(5))).thenReturn(List.of(1L, 2L));
        when(jobRepository.findAllByIds(List.of(1L, 2L))).thenReturn(List.of(first, second));
        when(storeRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(activeStore(30L)));
        when(jobRepository.existsActiveProcessingForStore(eq(30L), eq(1L), any())).thenReturn(false);

        List<TiendanubeClaimedJob> claimed = service.claimNextBatch();

        assertEquals(1, claimed.size());
        assertEquals(1L, claimed.getFirst().jobId());
        assertEquals(TiendanubeJobStatus.PENDING, second.getStatus());
    }

    @Test
    void skipsStoreWhenAnotherJobIsStillProcessing() {
        TiendanubeSyncJob job = job(1L, 30L, TiendanubeJobStatus.PENDING, 0, 7);
        when(jobRepository.findClaimableIds(any(), eq(5))).thenReturn(List.of(1L));
        when(jobRepository.findAllByIds(List.of(1L))).thenReturn(List.of(job));
        when(storeRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(activeStore(30L)));
        when(jobRepository.existsActiveProcessingForStore(eq(30L), eq(1L), any())).thenReturn(true);

        List<TiendanubeClaimedJob> claimed = service.claimNextBatch();

        assertEquals(0, claimed.size());
        assertEquals(TiendanubeJobStatus.PENDING, job.getStatus());
    }

    @Test
    void skipsStoreWhenConnectionIsNotUsable() {
        TiendanubeSyncJob job = job(1L, 30L, TiendanubeJobStatus.PENDING, 0, 7);
        when(jobRepository.findClaimableIds(any(), eq(5))).thenReturn(List.of(1L));
        when(jobRepository.findAllByIds(List.of(1L))).thenReturn(List.of(job));
        when(storeRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(
                TiendanubeStore.builder().id(30L).active(true).tokenValid(false).build()
        ));

        List<TiendanubeClaimedJob> claimed = service.claimNextBatch();

        assertEquals(0, claimed.size());
        assertEquals(TiendanubeJobStatus.PENDING, job.getStatus());
    }

    @Test
    void marksOpenAttemptStaleWhenExpiredLeaseIsReclaimed() {
        TiendanubeSyncJob job = job(1L, 30L, TiendanubeJobStatus.PROCESSING, 1, 7);
        job.setLeaseUntil(Instant.now().minusSeconds(1));
        prepareClaim(job);

        service.claimNextBatch();

        verify(attemptRepository).markProcessingAttempts(
                eq(1L), eq(TiendanubeJobAttemptStatus.STALE), eq(TiendanubeJobAttemptStatus.PROCESSING),
                any(), eq("LEASE_EXPIRED"), any()
        );
    }

    @Test
    void exhaustedExpiredJobBecomesFailedInsteadOfBeingReclaimed() {
        TiendanubeSyncJob job = job(1L, 30L, TiendanubeJobStatus.PROCESSING, 7, 7);
        job.setProcessingStartedAt(Instant.now().minusSeconds(30));
        job.setLeaseUntil(Instant.now().minusSeconds(1));
        prepareClaim(job);

        List<TiendanubeClaimedJob> claimed = service.claimNextBatch();

        assertEquals(0, claimed.size());
        assertEquals(TiendanubeJobStatus.FAILED, job.getStatus());
        assertNotNull(job.getCompletedAt());
        assertNull(job.getProcessingToken());
        assertNull(job.getLeaseUntil());
    }

    private void prepareClaim(TiendanubeSyncJob job) {
        when(jobRepository.findClaimableIds(any(), eq(5))).thenReturn(List.of(job.getId()));
        when(jobRepository.findAllByIds(List.of(job.getId()))).thenReturn(List.of(job));
        when(storeRepository.findByIdForUpdate(job.getTiendanubeStoreId()))
                .thenReturn(Optional.of(activeStore(job.getTiendanubeStoreId())));
        when(jobRepository.existsActiveProcessingForStore(eq(job.getTiendanubeStoreId()), eq(job.getId()), any()))
                .thenReturn(false);
    }

    private TiendanubeStore activeStore(Long id) {
        return TiendanubeStore.builder().id(id).active(true).tokenValid(true).build();
    }

    private TiendanubeSyncJob job(Long id, Long tiendanubeStoreId, TiendanubeJobStatus status, int attempts, int maxAttempts) {
        return TiendanubeSyncJob.builder()
                .id(id)
                .bookstoreId(2L)
                .tiendanubeStoreId(tiendanubeStoreId)
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
