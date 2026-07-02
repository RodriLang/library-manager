package com.rodrilang.librarymanager.integrations.tiendanube.service.impl;

import com.rodrilang.librarymanager.integrations.tiendanube.client.TiendanubeClient;
import com.rodrilang.librarymanager.integrations.tiendanube.config.TiendanubeProperties;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeTokenResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeStore;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeStoreRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.service.TiendanubeOAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class TiendanubeOAuthServiceImpl implements TiendanubeOAuthService {

    private final TiendanubeStoreRepository storeRepository;
    private final TiendanubeProperties properties;
    private final TiendanubeClient tiendanubeClient;

    public String buildAuthorizationUrl() {
        return properties.authUrl() + "/" + properties.clientId() + "/authorize";
    }

    @Override
    public void handleCallback(String code) {
        TiendanubeTokenResponse response = tiendanubeClient.exchangeCodeForToken(code);

        if (response == null) {
            throw new IllegalStateException("No se pudo obtener el token de Tiendanube");
        }

        TiendanubeStore store = storeRepository
                .findByStoreIdAndActiveTrue(response.userId())
                .orElseGet(TiendanubeStore::new);

        store.setStoreId(response.userId());
        store.setAccessToken(response.accessToken());
        store.setTokenType(response.tokenType());
        store.setScope(response.scope());
        store.setActive(true);
        store.setConnectedAt(LocalDateTime.now(ZoneId.systemDefault()));

        storeRepository.save(store);
    }
}