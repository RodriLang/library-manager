package com.rodrilang.librarymanager.integrations.tiendanube.event;

import java.util.List;

public record TiendanubePriceSyncRequestedEvent(
        List<Long> inventoryIds
) {
}