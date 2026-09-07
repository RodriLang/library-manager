package com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.service;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.dto.TiendanubeReconciliationItemResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.dto.TiendanubeReconciliationRunResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.repository.TiendanubeReconciliationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TiendanubeReconciliationManagementService {

    private final BookstoreContext bookstoreContext;
    private final TiendanubeReconciliationRequestService requestService;
    private final TiendanubeReconciliationRepository reconciliationRepository;

    public TiendanubeReconciliationRunResponse createManualRun() {
        return requestService.createManualRun();
    }

    @Transactional(readOnly = true)
    public Page<TiendanubeReconciliationRunResponse> getRuns(Pageable pageable) {
        return reconciliationRepository.findRuns(bookstoreContext.getCurrentBookstoreId(), pageable);
    }

    @Transactional(readOnly = true)
    public TiendanubeReconciliationRunResponse getRun(Long runId) {
        return reconciliationRepository.findRun(runId, bookstoreContext.getCurrentBookstoreId())
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró la reconciliación de Tiendanube"));
    }

    @Transactional(readOnly = true)
    public Page<TiendanubeReconciliationItemResponse> getItems(Long runId, Pageable pageable) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        reconciliationRepository.findRun(runId, bookstoreId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró la reconciliación de Tiendanube"));

        return reconciliationRepository.findItems(runId, bookstoreId, pageable);
    }
}
