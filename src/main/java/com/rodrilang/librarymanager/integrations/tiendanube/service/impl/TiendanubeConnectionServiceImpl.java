package com.rodrilang.librarymanager.integrations.tiendanube.service.impl;

import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeStoreRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.service.TiendanubeConnectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class TiendanubeConnectionServiceImpl implements TiendanubeConnectionService {

    private final TiendanubeStoreRepository storeRepository;

    @Override
    @Transactional
    public void markTokenValid(Long storeId) {
        storeRepository.findByStoreId(storeId).ifPresent(store -> {
            store.setTokenValid(true);
            store.setLastValidatedAt(Instant.now());
            store.setConnectionError(null);
        });
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markTokenInvalid(Long storeId, String error) {
        storeRepository.findByStoreId(storeId).ifPresent(store -> {
            store.setTokenValid(false);
            store.setLastValidatedAt(Instant.now());
            store.setConnectionError(error);
        });
    }
}