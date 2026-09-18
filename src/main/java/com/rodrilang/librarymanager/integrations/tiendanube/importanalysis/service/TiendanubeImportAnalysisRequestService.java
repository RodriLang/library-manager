package com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.service;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeStore;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.dto.TiendanubeImportAnalysisRunResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.repository.TiendanubeImportAnalysisRunRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeStoreRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.work.enums.TiendanubeWorkType;
import com.rodrilang.librarymanager.integrations.tiendanube.work.service.TiendanubeWorkNotifier;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class TiendanubeImportAnalysisRequestService {

    private final BookstoreContext bookstoreContext;
    private final TiendanubeStoreRepository storeRepository;
    private final TiendanubeImportAnalysisRunRepository runRepository;
    private final TiendanubeWorkNotifier workNotifier;

    @Value("${tiendanube.import-analysis.enabled:true}")
    private boolean enabled;

    @Transactional
    public TiendanubeImportAnalysisRunResponse createRun() {
        if (!enabled) {
            throw new BusinessException("El análisis de importación de Tiendanube está deshabilitado en este entorno");
        }

        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        TiendanubeStore store = requireConnectedStore(bookstoreId);
        Instant now = Instant.now();

        runRepository.createRun(
                bookstoreId,
                store.getId(),
                store.getStoreId(),
                now
        );

        workNotifier.notifyWork(TiendanubeWorkType.IMPORT_ANALYSIS);

        return runRepository.findActiveRun(bookstoreId, store.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "No se pudo crear ni recuperar el análisis de importación de Tiendanube"
                ));
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
