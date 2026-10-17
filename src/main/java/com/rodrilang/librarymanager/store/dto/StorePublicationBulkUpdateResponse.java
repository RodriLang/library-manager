package com.rodrilang.librarymanager.store.dto;

import java.util.List;

public record StorePublicationBulkUpdateResponse(
        int requested,
        int updated,
        int failed,
        List<StorePublicationBulkErrorResponse> errors
) {}
