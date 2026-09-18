package com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.service;

import com.rodrilang.librarymanager.integrations.tiendanube.client.TiendanubeClient;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeProductResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.model.TiendanubeClaimedImportAnalysisRun;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.model.TiendanubeImportAnalysisItemData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TiendanubeImportAnalysisProcessor {

    private final TiendanubeClient client;
    private final TiendanubeImportAnalysisMatchingService matchingService;
    private final TiendanubeImportAnalysisCompletionService completionService;

    public void process(TiendanubeClaimedImportAnalysisRun run) {
        try {
            // El snapshot remoto se obtiene fuera de una transacción de base de datos.
            List<TiendanubeProductResponse> products = client.getProducts(run.storeId());
            List<TiendanubeImportAnalysisItemData> items = matchingService.analyze(
                    run.bookstoreId(),
                    run.storeId(),
                    products
            );

            completionService.complete(run, items);

            log.info(
                    "Tiendanube import analysis completed. runId={} storeId={} products={} variants={}",
                    run.id(),
                    run.storeId(),
                    products.size(),
                    items.size()
            );
        } catch (RuntimeException exception) {
            log.error(
                    "Tiendanube import analysis failed. runId={} storeId={}",
                    run.id(),
                    run.storeId(),
                    exception
            );

            completionService.fail(run, exception);
        }
    }
}
