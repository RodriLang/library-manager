package com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.service;

import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.dto.TiendanubeClaimedReconciliationRun;
import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.dto.TiendanubeReconciliationIssue;
import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.repository.TiendanubeReconciliationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TiendanubeReconciliationCompletionService {

    private final TiendanubeReconciliationRepository reconciliationRepository;

    @Transactional
    public void complete(
            TiendanubeClaimedReconciliationRun run,
            int linkedCount,
            int checkedCount,
            List<TiendanubeReconciliationIssue> issues
    ) {
        Instant now = Instant.now();
        reconciliationRepository.replaceIssues(run.id(), issues, now);

        boolean updated = reconciliationRepository.markCompleted(
                run.id(),
                run.processingToken(),
                linkedCount,
                checkedCount,
                issues.size(),
                now
        );

        if (!updated) {
            throw new IllegalStateException("La reconciliación perdió su lease antes de completar: " + run.id());
        }
    }

    @Transactional
    public void fail(TiendanubeClaimedReconciliationRun run, int linkedCount, RuntimeException exception) {
        String errorType = exception.getClass().getSimpleName();
        String errorMessage = exception.getMessage() == null || exception.getMessage().isBlank()
                ? errorType
                : exception.getMessage();

        boolean updated = reconciliationRepository.markFailed(
                run.id(),
                run.processingToken(),
                linkedCount,
                errorType,
                errorMessage,
                Instant.now()
        );

        if (!updated) {
            log.warn("Ignoring stale Tiendanube reconciliation failure. runId={}", run.id());
        }
    }
}
