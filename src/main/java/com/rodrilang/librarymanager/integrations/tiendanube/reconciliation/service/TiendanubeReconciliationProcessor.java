package com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.service;

import com.rodrilang.librarymanager.integrations.tiendanube.client.TiendanubeClient;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeProductResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.dto.TiendanubeClaimedReconciliationRun;
import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.dto.TiendanubeReconciliationInventorySnapshot;
import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.dto.TiendanubeReconciliationIssue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TiendanubeReconciliationProcessor {

    private final TiendanubeReconciliationDataService dataService;
    private final TiendanubeClient client;
    private final TiendanubeReconciliationComparisonService comparisonService;
    private final TiendanubeReconciliationCompletionService completionService;

    public void process(TiendanubeClaimedReconciliationRun run) {
        int linkedCount = 0;

        try {
            List<TiendanubeReconciliationInventorySnapshot> localSnapshots = dataService.load(run);
            linkedCount = localSnapshots.size();

            // HTTP deliberadamente fuera de una transacción de base de datos.
            List<TiendanubeProductResponse> remoteProducts = client.getProducts(run.storeId());
            List<TiendanubeReconciliationIssue> issues = comparisonService.compare(localSnapshots, remoteProducts);

            completionService.complete(run, linkedCount, localSnapshots.size(), issues);

            log.info(
                    "Tiendanube reconciliation completed. runId={} storeId={} linked={} checked={} issues={}",
                    run.id(), run.storeId(), linkedCount, localSnapshots.size(), issues.size()
            );
        } catch (RuntimeException exception) {
            log.error(
                    "Tiendanube reconciliation failed. runId={} storeId={} linked={}",
                    run.id(), run.storeId(), linkedCount, exception
            );
            completionService.fail(run, linkedCount, exception);
        }
    }
}
