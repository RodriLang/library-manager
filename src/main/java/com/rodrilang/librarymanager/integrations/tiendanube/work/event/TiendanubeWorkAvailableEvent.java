package com.rodrilang.librarymanager.integrations.tiendanube.work.event;

import com.rodrilang.librarymanager.integrations.tiendanube.work.enums.TiendanubeWorkType;

public record TiendanubeWorkAvailableEvent(TiendanubeWorkType type) {
}
