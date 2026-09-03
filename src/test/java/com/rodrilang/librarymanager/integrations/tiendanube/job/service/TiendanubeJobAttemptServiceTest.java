package com.rodrilang.librarymanager.integrations.tiendanube.job.service;

import com.rodrilang.librarymanager.integrations.tiendanube.job.dto.TiendanubeJobExecutionContext;
import com.rodrilang.librarymanager.integrations.tiendanube.job.entity.TiendanubeSyncAttempt;
import com.rodrilang.librarymanager.integrations.tiendanube.job.entity.TiendanubeSyncJob;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TiendanubeJobAttemptServiceTest {

    @Mock
    private TiendanubeSyncJobRepository jobRepository;

    @Mock
    private TiendanubeSyncAttemptRepository attemptRepository;

    @Test
    void startsAttemptOnlyForCurrentProcessingToken() {
        UUID token = UUID.randomUUID();
        TiendanubeSyncJob job = processingJob(token);
        when(jobRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(job));
        when(attemptRepository.save(any())).thenAnswer(invocation -> {
            TiendanubeSyncAttempt attempt = invocation.getArgument(0);
            attempt.setId(10L);
            return attempt;
        });

        TiendanubeJobAttemptService service = new TiendanubeJobAttemptService(jobRepository, attemptRepository);
        Optional<TiendanubeJobExecutionContext> result = service.startAttempt(1L, token);

        assertTrue(result.isPresent());
        assertEquals(1, job.getAttemptCount());
        assertEquals(1, result.get().attemptNumber());
        assertEquals(10L, result.get().attemptId());
    }

    @Test
    void ignoresStaleProcessingToken() {
        TiendanubeSyncJob job = processingJob(UUID.randomUUID());
        when(jobRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(job));

        TiendanubeJobAttemptService service = new TiendanubeJobAttemptService(jobRepository, attemptRepository);
        Optional<TiendanubeJobExecutionContext> result = service.startAttempt(1L, UUID.randomUUID());

        assertTrue(result.isEmpty());
        assertEquals(0, job.getAttemptCount());
        verify(attemptRepository, never()).save(any());
    }

    private TiendanubeSyncJob processingJob(UUID token) {
        return TiendanubeSyncJob.builder()
                .id(1L)
                .bookstoreId(2L)
                .storeId(3L)
                .inventoryId(4L)
                .type(TiendanubeJobType.SYNC_STOCK)
                .source(TiendanubeJobSource.AUTOMATIC)
                .status(TiendanubeJobStatus.PROCESSING)
                .attemptCount(0)
                .maxAttempts(7)
                .nextAttemptAt(Instant.now())
                .processingStartedAt(Instant.now())
                .leaseUntil(Instant.now().plusSeconds(60))
                .processingToken(token)
                .build();
    }
}
