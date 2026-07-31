package com.rodrilang.librarymanager.integrations.tiendanube.dto.response;

import com.rodrilang.librarymanager.integrations.tiendanube.enums.TiendanubeConnectionStatus;

public record TiendanubeConnectionStatusResponse(
        boolean connected,
        boolean requiresReconnect,
        Long storeId,
        TiendanubeConnectionStatus status,
        String message
) {
}