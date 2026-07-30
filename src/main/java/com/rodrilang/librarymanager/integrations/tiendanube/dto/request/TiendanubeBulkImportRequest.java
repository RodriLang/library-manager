package com.rodrilang.librarymanager.integrations.tiendanube.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record TiendanubeBulkImportRequest(
        @NotEmpty List<@Valid TiendanubeImportItemRequest> items
) {
}