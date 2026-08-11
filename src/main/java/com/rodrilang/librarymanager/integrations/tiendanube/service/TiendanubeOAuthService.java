package com.rodrilang.librarymanager.integrations.tiendanube.service;

import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeAuthorizationResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeConnectionStatusResponse;

public interface TiendanubeOAuthService {

    TiendanubeAuthorizationResponse createAuthorizationUrl();

    void handleCallback(String code, String state);

    TiendanubeConnectionStatusResponse getStatus();

    void disconnect();
}