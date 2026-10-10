package com.rodrilang.librarymanager.store.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BulkUpdateStorePublicationsRequest(
        @NotEmpty @Size(max = 500) List<Long> inventoryIds,
        Boolean published,
        Boolean featured
) {
    public BulkUpdateStorePublicationsRequest {
        inventoryIds = inventoryIds == null
                ? List.of()
                : inventoryIds.stream().filter(id -> id != null && id > 0).distinct().toList();
    }
}
