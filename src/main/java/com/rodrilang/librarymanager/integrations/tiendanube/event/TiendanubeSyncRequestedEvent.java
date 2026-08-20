package com.rodrilang.librarymanager.integrations.tiendanube.event;

import com.rodrilang.librarymanager.integrations.tiendanube.enums.TiendanubeSyncType;

public record TiendanubeSyncRequestedEvent(
        Long inventoryId,
        TiendanubeSyncType type
) {
}