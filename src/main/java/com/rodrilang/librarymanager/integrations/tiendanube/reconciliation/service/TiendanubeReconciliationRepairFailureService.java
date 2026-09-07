package com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.service;

import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.dto.TiendanubeReconciliationRepairCandidate;
import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.enums.TiendanubeReconciliationRepairSource;
import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.repository.TiendanubeReconciliationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class TiendanubeReconciliationRepairFailureService {

    private final TiendanubeReconciliationRepository reconciliationRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recordFailure(
            Long runId,
            Long itemId,
            Long bookstoreId,
            TiendanubeReconciliationRepairSource source,
            RuntimeException exception
    ) {
        TiendanubeReconciliationRepairCandidate candidate = reconciliationRepository
                .findRepairCandidateForUpdate(runId, itemId, bookstoreId)
                .orElse(null);

        if (candidate == null || candidate.repairRequestedAt() != null) {
            return false;
        }

        String errorType = exception.getClass().getSimpleName();
        String errorMessage = exception.getMessage() == null || exception.getMessage().isBlank()
                ? errorType
                : exception.getMessage();

        return reconciliationRepository.markRepairFailed(itemId, source, errorType, errorMessage, Instant.now());
    }
}
