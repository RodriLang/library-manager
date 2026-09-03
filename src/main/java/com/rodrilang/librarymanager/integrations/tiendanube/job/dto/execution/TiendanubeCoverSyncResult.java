package com.rodrilang.librarymanager.integrations.tiendanube.job.dto.execution;

public record TiendanubeCoverSyncResult(Long imageId, String coverUrl) {

    public static TiendanubeCoverSyncResult unchanged() {
        return new TiendanubeCoverSyncResult(null, null);
    }
}
