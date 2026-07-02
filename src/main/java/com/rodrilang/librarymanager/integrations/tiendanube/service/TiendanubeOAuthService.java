package com.rodrilang.librarymanager.integrations.tiendanube.service;

public interface TiendanubeOAuthService {

    String buildAuthorizationUrl();

    void handleCallback(String code);

}