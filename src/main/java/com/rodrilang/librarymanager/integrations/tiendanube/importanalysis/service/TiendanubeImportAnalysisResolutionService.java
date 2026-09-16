package com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.service;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.dto.TiendanubeImportAnalysisBulkFailureResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.dto.TiendanubeImportAnalysisBulkResolveResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.dto.TiendanubeImportAnalysisCreateInventoryRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.dto.TiendanubeImportAnalysisItemResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.dto.TiendanubeImportAnalysisResolveRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.dto.TiendanubeImportAnalysisRunResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.enums.TiendanubeImportAnalysisRunStatus;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.model.TiendanubeImportAnalysisReadyItem;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.repository.TiendanubeImportAnalysisItemWriteRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.repository.TiendanubeImportAnalysisQueryRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.repository.TiendanubeImportAnalysisRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TiendanubeImportAnalysisResolutionService {

    private final BookstoreContext bookstoreContext;
    private final TiendanubeImportAnalysisRunRepository runRepository;
    private final TiendanubeImportAnalysisItemWriteRepository itemWriteRepository;
    private final TiendanubeImportAnalysisQueryRepository queryRepository;
    private final TiendanubeImportAnalysisResolutionItemService itemService;

    public TiendanubeImportAnalysisItemResponse resolve(
            Long runId,
            Long itemId,
            TiendanubeImportAnalysisResolveRequest request
    ) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();

        itemService.resolve(
                runId,
                itemId,
                bookstoreId,
                request.inventoryId(),
                request.shouldSyncStock(),
                true
        );

        return requireItem(runId, itemId, bookstoreId);
    }

    public TiendanubeImportAnalysisItemResponse createInventoryAndResolve(
            Long runId,
            Long itemId,
            TiendanubeImportAnalysisCreateInventoryRequest request
    ) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();

        itemService.createInventoryAndResolve(
                runId,
                itemId,
                bookstoreId,
                request
        );

        return requireItem(runId, itemId, bookstoreId);
    }

    public TiendanubeImportAnalysisItemResponse ignore(Long runId, Long itemId) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        itemService.ignore(runId, itemId, bookstoreId);
        return requireItem(runId, itemId, bookstoreId);
    }

    public TiendanubeImportAnalysisBulkResolveResponse linkReady(
            Long runId,
            boolean syncStock
    ) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        TiendanubeImportAnalysisRunResponse run = runRepository.findRun(runId, bookstoreId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No se encontró el análisis Tiendanube con ID: " + runId
                ));

        if (run.status() != TiendanubeImportAnalysisRunStatus.COMPLETED) {
            throw new BusinessException("El análisis debe estar completado antes de vincular coincidencias");
        }

        List<TiendanubeImportAnalysisReadyItem> readyItems = itemWriteRepository.findReadyItems(runId, bookstoreId);
        List<TiendanubeImportAnalysisBulkFailureResponse> failures = new ArrayList<>();
        int linked = 0;

        for (TiendanubeImportAnalysisReadyItem readyItem : readyItems) {
            try {
                itemService.resolve(
                        runId,
                        readyItem.itemId(),
                        bookstoreId,
                        readyItem.inventoryId(),
                        syncStock,
                        false
                );
                linked++;
            } catch (RuntimeException exception) {
                failures.add(new TiendanubeImportAnalysisBulkFailureResponse(
                        readyItem.itemId(),
                        exception.getMessage() == null
                                ? exception.getClass().getSimpleName()
                                : exception.getMessage()
                ));
            }
        }

        if (!readyItems.isEmpty()) {
            itemService.refreshCounts(runId);
        }

        return new TiendanubeImportAnalysisBulkResolveResponse(
                readyItems.size(),
                linked,
                failures.size(),
                failures
        );
    }

    private TiendanubeImportAnalysisItemResponse requireItem(
            Long runId,
            Long itemId,
            Long bookstoreId
    ) {
        return queryRepository.findItem(runId, itemId, bookstoreId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No se encontró el caso de análisis Tiendanube con ID: " + itemId
                ));
    }
}
