package com.rodrilang.librarymanager.integrations.tiendanube.job.service;

import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeStore;
import com.rodrilang.librarymanager.integrations.tiendanube.job.dto.TiendanubeJobExecutionContext;
import com.rodrilang.librarymanager.integrations.tiendanube.job.exception.TiendanubeJobExecutionException;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeStoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class TiendanubeJobConnectionGuard {

    private final TiendanubeStoreRepository storeRepository;

    @Transactional(readOnly = true)
    public void validate(TiendanubeJobExecutionContext context) {
        TiendanubeStore store = storeRepository.findById(context.tiendanubeStoreId())
                .orElseThrow(() -> blocked("STORE_NOT_FOUND", "La conexión Tiendanube del job ya no existe"));

        if (!store.isActive()) {
            throw blocked("STORE_DISCONNECTED", "La cuenta Tiendanube está desconectada");
        }

        if (!store.isTokenValid()) {
            throw blocked("INVALID_TOKEN", "La conexión con Tiendanube necesita volver a autorizarse");
        }

        if (!Objects.equals(store.getStoreId(), context.storeId())) {
            throw blocked("STORE_CHANGED", "El job pertenece a una cuenta Tiendanube anterior");
        }

        if (!Objects.equals(store.getBookstore().getId(), context.bookstoreId())) {
            throw blocked("BOOKSTORE_MISMATCH", "La conexión Tiendanube ya no pertenece a la librería del job");
        }
    }

    private TiendanubeJobExecutionException blocked(String type, String message) {
        return TiendanubeJobExecutionException.blocked(type, message, null, null);
    }
}
