package com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.service;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.dto.TiendanubeReconciliationRepairCandidate;
import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.dto.TiendanubeReconciliationRepairResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.dto.TiendanubeReconciliationRunResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.enums.TiendanubeReconciliationIssueType;
import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.enums.TiendanubeReconciliationRepairRequestResult;
import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.enums.TiendanubeReconciliationRepairSource;
import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.enums.TiendanubeReconciliationSource;
import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.enums.TiendanubeReconciliationStatus;
import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.repository.TiendanubeReconciliationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TiendanubeReconciliationRepairServiceTest {

    @Mock private BookstoreContext bookstoreContext;
    @Mock private TiendanubeReconciliationRepository reconciliationRepository;
    @Mock private TiendanubeReconciliationRepairItemService repairItemService;
    @Mock private TiendanubeReconciliationRepairFailureService failureService;

    @Test
    void manualRunQueuesOnlyRepairableIssuesAndLeavesMissingRemoteForReview() {
        when(bookstoreContext.getCurrentBookstoreId()).thenReturn(2L);
        when(reconciliationRepository.findRun(2L, 2L)).thenReturn(Optional.of(completedRun()));

        TiendanubeReconciliationRepairCandidate stock = candidate(5L, 7L, TiendanubeReconciliationIssueType.STOCK_MISMATCH);
        TiendanubeReconciliationRepairCandidate price = candidate(6L, 7L, TiendanubeReconciliationIssueType.PRICE_MISMATCH);
        TiendanubeReconciliationRepairCandidate missing = candidate(7L, 10L, TiendanubeReconciliationIssueType.REMOTE_PRODUCT_MISSING);
        TiendanubeReconciliationRepairCandidate stockTwo = candidate(8L, 11L, TiendanubeReconciliationIssueType.STOCK_MISMATCH);
        when(reconciliationRepository.findRepairCandidates(2L, 2L)).thenReturn(List.of(stock, price, missing, stockTwo));
        when(repairItemService.requestRepair(2L, 5L, 2L, TiendanubeReconciliationRepairSource.MANUAL))
                .thenReturn(TiendanubeReconciliationRepairRequestResult.QUEUED);
        when(repairItemService.requestRepair(2L, 6L, 2L, TiendanubeReconciliationRepairSource.MANUAL))
                .thenReturn(TiendanubeReconciliationRepairRequestResult.QUEUED);
        when(repairItemService.requestRepair(2L, 8L, 2L, TiendanubeReconciliationRepairSource.MANUAL))
                .thenReturn(TiendanubeReconciliationRepairRequestResult.QUEUED);

        TiendanubeReconciliationRepairResponse response = service().repairManualRun(2L);

        assertEquals(3, response.repairableCount());
        assertEquals(3, response.queuedCount());
        assertEquals(0, response.alreadyRequestedCount());
        assertEquals(1, response.manualReviewCount());
        assertEquals(0, response.failedCount());
        verify(repairItemService).requestRepair(2L, 5L, 2L, TiendanubeReconciliationRepairSource.MANUAL);
        verify(repairItemService).requestRepair(2L, 6L, 2L, TiendanubeReconciliationRepairSource.MANUAL);
        verify(repairItemService).requestRepair(2L, 8L, 2L, TiendanubeReconciliationRepairSource.MANUAL);
    }

    private TiendanubeReconciliationRunResponse completedRun() {
        return new TiendanubeReconciliationRunResponse(
                2L,
                7948456L,
                TiendanubeReconciliationSource.MANUAL,
                TiendanubeReconciliationStatus.COMPLETED,
                10,
                10,
                4,
                null,
                null,
                Instant.parse("2026-09-07T18:10:57Z"),
                Instant.parse("2026-09-07T18:10:59Z"),
                Instant.parse("2026-09-07T18:11:00Z")
        );
    }

    private TiendanubeReconciliationRepairCandidate candidate(
            Long itemId,
            Long inventoryId,
            TiendanubeReconciliationIssueType type
    ) {
        return new TiendanubeReconciliationRepairCandidate(
                itemId,
                2L,
                2L,
                30L,
                7948456L,
                inventoryId,
                inventoryId,
                1000L + inventoryId,
                2000L + inventoryId,
                type,
                null
        );
    }

    private TiendanubeReconciliationRepairService service() {
        return new TiendanubeReconciliationRepairService(
                bookstoreContext,
                reconciliationRepository,
                repairItemService,
                failureService
        );
    }
}
