package com.rodrilang.librarymanager.integrations.tiendanube.dto.response;

import java.util.List;

public record TiendanubeBulkImportResponse(
        int requested,
        int imported,
        int linked,
        int failed,
        List<TiendanubeImportItemResultResponse> results
) {
}