package com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.service;

import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeProductLink;
import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeStore;
import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobType;
import com.rodrilang.librarymanager.integrations.tiendanube.job.service.TiendanubeJobRequestService;
import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.dto.TiendanubeReconciliationRepairCandidate;
import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.enums.TiendanubeReconciliationIssueType;
import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.enums.TiendanubeReconciliationRepairRequestResult;
import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.enums.TiendanubeReconciliationRepairSource;
import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.repository.TiendanubeReconciliationRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeProductLinkRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeStoreRepository;
import com.rodrilang.librarymanager.model.Bookstore;
import com.rodrilang.librarymanager.model.Inventory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TiendanubeReconciliationRepairItemServiceTest {

    @Mock private TiendanubeReconciliationRepository reconciliationRepository;
    @Mock private TiendanubeProductLinkRepository productLinkRepository;
    @Mock private TiendanubeStoreRepository storeRepository;
    @Mock private TiendanubeJobRequestService jobRequestService;
    @Mock private TiendanubeProductLink link;
    @Mock private TiendanubeStore store;
    @Mock private Inventory inventory;
    @Mock private Bookstore bookstore;

    @Test
    void manualStockMismatchQueuesExistingStockJobEngine() {
        TiendanubeReconciliationRepairCandidate candidate = candidate(TiendanubeReconciliationIssueType.STOCK_MISMATCH, null);
        prepareCurrentState(candidate);
        when(jobRequestService.enqueueManualLinked(11L, TiendanubeJobType.SYNC_STOCK)).thenReturn(99L);
        when(reconciliationRepository.markRepairQueued(eq(5L), eq(TiendanubeReconciliationRepairSource.MANUAL), eq(99L), any()))
                .thenReturn(true);

        TiendanubeReconciliationRepairRequestResult result = service().requestRepair(
                2L, 5L, 2L, TiendanubeReconciliationRepairSource.MANUAL
        );

        assertEquals(TiendanubeReconciliationRepairRequestResult.QUEUED, result);
        verify(jobRequestService).enqueueManualLinked(11L, TiendanubeJobType.SYNC_STOCK);
        verify(reconciliationRepository).markRepairQueued(
                eq(5L), eq(TiendanubeReconciliationRepairSource.MANUAL), eq(99L), any(Instant.class)
        );
    }

    @Test
    void automaticPriceMismatchQueuesAutomaticPriceJob() {
        TiendanubeReconciliationRepairCandidate candidate = candidate(TiendanubeReconciliationIssueType.PRICE_MISMATCH, null);
        prepareCurrentState(candidate);
        when(link.getInventory()).thenReturn(inventory);
        when(inventory.getTiendanubePriceSyncEnabled()).thenReturn(true);
        when(jobRequestService.enqueueAutomaticLinked(11L, TiendanubeJobType.SYNC_PRICE)).thenReturn(Optional.of(100L));
        when(reconciliationRepository.markRepairQueued(eq(5L), eq(TiendanubeReconciliationRepairSource.AUTOMATIC), eq(100L), any()))
                .thenReturn(true);

        TiendanubeReconciliationRepairRequestResult result = service().requestRepair(
                2L, 5L, 2L, TiendanubeReconciliationRepairSource.AUTOMATIC
        );

        assertEquals(TiendanubeReconciliationRepairRequestResult.QUEUED, result);
        verify(jobRequestService).enqueueAutomaticLinked(11L, TiendanubeJobType.SYNC_PRICE);
    }

    @Test
    void priceMismatchIsNotQueuedIfPriceSyncWasDisabledAfterSnapshot() {
        TiendanubeReconciliationRepairCandidate candidate = candidate(TiendanubeReconciliationIssueType.PRICE_MISMATCH, null);
        prepareCurrentState(candidate);
        when(link.getInventory()).thenReturn(inventory);
        when(inventory.getTiendanubePriceSyncEnabled()).thenReturn(false);

        assertThrows(BusinessException.class, () -> service().requestRepair(
                2L, 5L, 2L, TiendanubeReconciliationRepairSource.MANUAL
        ));

        verify(jobRequestService, never()).enqueueManualLinked(any(), any());
        verify(jobRequestService, never()).enqueueAutomaticLinked(anyLong(), any(TiendanubeJobType.class));
    }

    @Test
    void alreadyRequestedItemDoesNotQueueAgain() {
        TiendanubeReconciliationRepairCandidate candidate = candidate(
                TiendanubeReconciliationIssueType.STOCK_MISMATCH,
                Instant.parse("2026-09-07T18:11:00Z")
        );
        when(reconciliationRepository.findRepairCandidateForUpdate(2L, 5L, 2L)).thenReturn(Optional.of(candidate));

        TiendanubeReconciliationRepairRequestResult result = service().requestRepair(
                2L, 5L, 2L, TiendanubeReconciliationRepairSource.MANUAL
        );

        assertEquals(TiendanubeReconciliationRepairRequestResult.ALREADY_REQUESTED, result);
        verify(jobRequestService, never()).enqueueManualLinked(any(), any());
        verify(jobRequestService, never()).enqueueAutomaticLinked(anyLong(), any(TiendanubeJobType.class));
    }

    @Test
    void missingRemoteProductStaysManualReview() {
        TiendanubeReconciliationRepairCandidate candidate = candidate(
                TiendanubeReconciliationIssueType.REMOTE_PRODUCT_MISSING,
                null
        );
        when(reconciliationRepository.findRepairCandidateForUpdate(2L, 5L, 2L)).thenReturn(Optional.of(candidate));

        TiendanubeReconciliationRepairRequestResult result = service().requestRepair(
                2L, 5L, 2L, TiendanubeReconciliationRepairSource.AUTOMATIC
        );

        assertEquals(TiendanubeReconciliationRepairRequestResult.MANUAL_REVIEW, result);
        verify(jobRequestService, never()).enqueueAutomaticLinked(anyLong(), any(TiendanubeJobType.class));
        verify(jobRequestService, never()).enqueueManualLinked(any(), any());
    }

    private void prepareCurrentState(TiendanubeReconciliationRepairCandidate candidate) {
        when(reconciliationRepository.findRepairCandidateForUpdate(2L, 5L, 2L)).thenReturn(Optional.of(candidate));
        when(storeRepository.findById(30L)).thenReturn(Optional.of(store));
        when(store.isActive()).thenReturn(true);
        when(store.isTokenValid()).thenReturn(true);
        when(store.getStoreId()).thenReturn(7948456L);
        when(store.getBookstore()).thenReturn(bookstore);
        when(bookstore.getId()).thenReturn(2L);
        when(productLinkRepository.findByInventoryIdAndActiveTrue(11L)).thenReturn(Optional.of(link));
        when(link.getId()).thenReturn(10L);
        when(link.getTiendanubeStoreId()).thenReturn(7948456L);
        when(link.getTiendanubeProductId()).thenReturn(358525030L);
        when(link.getTiendanubeVariantId()).thenReturn(1568313931L);
    }

    private TiendanubeReconciliationRepairCandidate candidate(
            TiendanubeReconciliationIssueType issueType,
            Instant repairRequestedAt
    ) {
        return new TiendanubeReconciliationRepairCandidate(
                5L,
                2L,
                2L,
                30L,
                7948456L,
                11L,
                10L,
                358525030L,
                1568313931L,
                issueType,
                repairRequestedAt
        );
    }

    private TiendanubeReconciliationRepairItemService service() {
        return new TiendanubeReconciliationRepairItemService(
                reconciliationRepository,
                productLinkRepository,
                storeRepository,
                jobRequestService
        );
    }
}
