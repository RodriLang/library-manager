package com.rodrilang.librarymanager.integrations.tiendanube.service;

public interface TiendanubeOAuthStateService {

    String create(Long bookstoreId);

    Long validateAndConsume(String state);
}