package com.rodrilang.librarymanager.integrations.tiendanube.job.dto.execution;

public record TiendanubeCoverSyncResult(Long imageId, String coverUrl, boolean changed) {

    public static TiendanubeCoverSyncResult unchanged() {
        return new TiendanubeCoverSyncResult(null, null, false);
    }

    public static TiendanubeCoverSyncResult synced(Long imageId, String coverUrl) {
        return new TiendanubeCoverSyncResult(imageId, coverUrl, true);
    }

    public static TiendanubeCoverSyncResult removed() {
        return new TiendanubeCoverSyncResult(null, null, true);
    }
}
