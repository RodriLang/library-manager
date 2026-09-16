package com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.service;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.dto.TiendanubeImportAnalysisItemResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.dto.TiendanubeImportAnalysisRunResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.enums.TiendanubeImportAnalysisItemStatus;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.enums.TiendanubeImportAnalysisMatchType;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.repository.TiendanubeImportAnalysisQueryRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.repository.TiendanubeImportAnalysisRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TiendanubeImportAnalysisManagementService {

    private final BookstoreContext bookstoreContext;
    private final TiendanubeImportAnalysisRequestService requestService;
    private final TiendanubeImportAnalysisRunRepository runRepository;
    private final TiendanubeImportAnalysisQueryRepository queryRepository;

    public TiendanubeImportAnalysisRunResponse createRun() {
        return requestService.createRun();
    }

    @Transactional(readOnly = true)
    public Page<TiendanubeImportAnalysisRunResponse> getRuns(Pageable pageable) {
        return runRepository.findRuns(bookstoreContext.getCurrentBookstoreId(), pageable);
    }

    @Transactional(readOnly = true)
    public TiendanubeImportAnalysisRunResponse getRun(Long runId) {
        return runRepository.findRun(runId, bookstoreContext.getCurrentBookstoreId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No se encontró el análisis Tiendanube con ID: " + runId
                ));
    }

    @Transactional(readOnly = true)
    public Page<TiendanubeImportAnalysisItemResponse> getItems(
            Long runId,
            TiendanubeImportAnalysisItemStatus status,
            TiendanubeImportAnalysisMatchType matchType,
            Boolean missingIdentifier,
            String query,
            Pageable pageable
    ) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        requireRun(runId, bookstoreId);

        return queryRepository.findItems(
                runId,
                bookstoreId,
                status,
                matchType,
                missingIdentifier,
                query,
                pageable
        );
    }

    @Transactional(readOnly = true)
    public TiendanubeImportAnalysisItemResponse getItem(Long runId, Long itemId) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        requireRun(runId, bookstoreId);

        return queryRepository.findItem(runId, itemId, bookstoreId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No se encontró el caso Tiendanube con ID: " + itemId
                ));
    }

    private void requireRun(Long runId, Long bookstoreId) {
        runRepository.findRun(runId, bookstoreId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No se encontró el análisis Tiendanube con ID: " + runId
                ));
    }
}
