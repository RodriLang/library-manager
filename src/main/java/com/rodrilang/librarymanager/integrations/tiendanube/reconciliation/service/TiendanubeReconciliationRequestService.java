package com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.service;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeStore;
import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.dto.TiendanubeReconciliationRunResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.enums.TiendanubeReconciliationSource;
import com.rodrilang.librarymanager.integrations.tiendanube.reconciliation.repository.TiendanubeReconciliationRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeStoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class TiendanubeReconciliationRequestService {

    private final BookstoreContext bookstoreContext;
    private final TiendanubeStoreRepository storeRepository;
    private final TiendanubeReconciliationRepository reconciliationRepository;

    @Value("${tiendanube.reconciliation.enabled:false}")
    private boolean enabled;

    @Transactional
    public TiendanubeReconciliationRunResponse createManualRun() {
        if (!enabled) {
            throw new BusinessException("La reconciliación de Tiendanube está deshabilitada en este entorno");
        }

        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        TiendanubeStore store = requireConnectedStore(bookstoreId);
        return createOrGetActive(bookstoreId, store, TiendanubeReconciliationSource.MANUAL, Instant.now());
    }

    @Transactional
    public void createScheduledRuns() {
        Instant now = Instant.now();

        for (TiendanubeStore store : storeRepository.findAllByActiveTrueAndTokenValidTrue()) {
            createOrGetActive(
                    store.getBookstore().getId(),
                    store,
                    TiendanubeReconciliationSource.SCHEDULED,
                    now
            );
        }
    }

    private TiendanubeReconciliationRunResponse createOrGetActive(
            Long bookstoreId,
            TiendanubeStore store,
            TiendanubeReconciliationSource source,
            Instant now
    ) {
        reconciliationRepository.createRun(bookstoreId, store.getId(), store.getStoreId(), source, now);

        return reconciliationRepository.findActiveRun(bookstoreId, store.getId())
                .orElseThrow(() -> new IllegalStateException("No se pudo crear ni recuperar la reconciliación de Tiendanube"));
    }

    private TiendanubeStore requireConnectedStore(Long bookstoreId) {
        TiendanubeStore store = storeRepository.findByBookstoreIdAndActiveTrue(bookstoreId)
                .orElseThrow(() -> new BusinessException("La librería no tiene una cuenta Tiendanube vinculada"));

        if (!store.isTokenValid()) {
            throw new BusinessException("La conexión con Tiendanube necesita volver a autorizarse");
        }

        return store;
    }
}
