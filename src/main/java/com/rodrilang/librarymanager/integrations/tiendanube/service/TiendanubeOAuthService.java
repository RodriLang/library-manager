package com.rodrilang.librarymanager.integrations.tiendanube.service;

import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeAuthorizationResponse;

public interface TiendanubeOAuthService {

    TiendanubeAuthorizationResponse createAuthorizationUrl();

    void handleCallback(String code, String state);
}