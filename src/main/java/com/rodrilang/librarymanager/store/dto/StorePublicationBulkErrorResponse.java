package com.rodrilang.librarymanager.store.dto;

public record StorePublicationBulkErrorResponse(
        Long inventoryId,
        String message
) {}
