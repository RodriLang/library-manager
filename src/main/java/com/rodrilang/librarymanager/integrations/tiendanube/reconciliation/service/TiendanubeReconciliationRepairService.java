package com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.service;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.dto.TiendanubeReconciliationItemResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.dto.TiendanubeReconciliationRepairCandidate;
import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.dto.TiendanubeReconciliationRepairResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.dto.TiendanubeReconciliationRunResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.enums.TiendanubeReconciliationIssueType;
import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.enums.TiendanubeReconciliationRepairRequestResult;
import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.enums.TiendanubeReconciliationRepairSource;
import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.enums.TiendanubeReconciliationStatus;
import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.repository.TiendanubeReconciliationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TiendanubeReconciliationRepairService {

    private final BookstoreContext bookstoreContext;
    private final TiendanubeReconciliationRepository reconciliationRepository;
    private final TiendanubeReconciliationRepairItemService repairItemService;
    private final TiendanubeReconciliationRepairFailureService failureService;

    public TiendanubeReconciliationRepairResponse repairManualRun(Long runId) {
        return repairRun(runId, bookstoreContext.getCurrentBookstoreId(), TiendanubeReconciliationRepairSource.MANUAL);
    }

    public TiendanubeReconciliationRepairResponse repairAutomaticRun(Long runId, Long bookstoreId) {
        return repairRun(runId, bookstoreId, TiendanubeReconciliationRepairSource.AUTOMATIC);
    }

    public TiendanubeReconciliationItemResponse repairManualItem(Long runId, Long itemId) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        requireCompletedRun(runId, bookstoreId);

        TiendanubeReconciliationRepairCandidate candidate = reconciliationRepository
                .findRepairCandidate(runId, itemId, bookstoreId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el hallazgo de reconciliación"));

        if (!isRepairable(candidate.issueType())) {
            throw new BusinessException("Este hallazgo requiere revisión manual y no puede repararse automáticamente");
        }

        if (candidate.repairRequestedAt() == null) {
            try {
                repairItemService.requestRepair(runId, itemId, bookstoreId, TiendanubeReconciliationRepairSource.MANUAL);
            } catch (RuntimeException exception) {
                failureService.recordFailure(
                        runId,
                        itemId,
                        bookstoreId,
                        TiendanubeReconciliationRepairSource.MANUAL,
                        exception
                );
            }
        }

        return reconciliationRepository.findItem(runId, itemId, bookstoreId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el hallazgo de reconciliación"));
    }

    private TiendanubeReconciliationRepairResponse repairRun(
            Long runId,
            Long bookstoreId,
            TiendanubeReconciliationRepairSource source
    ) {
        requireCompletedRun(runId, bookstoreId);
        List<TiendanubeReconciliationRepairCandidate> candidates = reconciliationRepository
                .findRepairCandidates(runId, bookstoreId);

        int repairable = 0;
        int queued = 0;
        int alreadyRequested = 0;
        int manualReview = 0;
        int failed = 0;

        for (TiendanubeReconciliationRepairCandidate candidate : candidates) {
            if (!isRepairable(candidate.issueType())) {
                manualReview++;
                continue;
            }

            repairable++;

            if (candidate.repairRequestedAt() != null) {
                alreadyRequested++;
                continue;
            }

            try {
                TiendanubeReconciliationRepairRequestResult result = repairItemService.requestRepair(
                        runId,
                        candidate.itemId(),
                        bookstoreId,
                        source
                );

                if (result == TiendanubeReconciliationRepairRequestResult.QUEUED) {
                    queued++;
                } else if (result == TiendanubeReconciliationRepairRequestResult.ALREADY_REQUESTED) {
                    alreadyRequested++;
                } else if (result == TiendanubeReconciliationRepairRequestResult.MANUAL_REVIEW) {
                    manualReview++;
                }
            } catch (RuntimeException exception) {
                boolean recorded = failureService.recordFailure(
                        runId,
                        candidate.itemId(),
                        bookstoreId,
                        source,
                        exception
                );

                if (recorded) {
                    failed++;
                } else {
                    alreadyRequested++;
                }

                log.warn(
                        "Could not queue Tiendanube reconciliation repair. runId={} itemId={} inventoryId={} type={}",
                        runId,
                        candidate.itemId(),
                        candidate.inventoryId(),
                        candidate.issueType(),
                        exception
                );
            }
        }

        return new TiendanubeReconciliationRepairResponse(
                runId,
                repairable,
                queued,
                alreadyRequested,
                manualReview,
                failed
        );
    }

    private TiendanubeReconciliationRunResponse requireCompletedRun(Long runId, Long bookstoreId) {
        TiendanubeReconciliationRunResponse run = reconciliationRepository.findRun(runId, bookstoreId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró la reconciliación de Tiendanube"));

        if (run.status() != TiendanubeReconciliationStatus.COMPLETED) {
            throw new BusinessException("La reconciliación debe estar completada antes de solicitar reparaciones");
        }

        return run;
    }

    private boolean isRepairable(TiendanubeReconciliationIssueType issueType) {
        return issueType == TiendanubeReconciliationIssueType.STOCK_MISMATCH
                || issueType == TiendanubeReconciliationIssueType.PRICE_MISMATCH;
    }
}
