package com.rodrilang.librarymanager.integrations.tiendanube.service;

public interface TiendanubeConnectionService {

    void markTokenValid(Long storeId);

    void markTokenInvalid(Long storeId, String error);
}