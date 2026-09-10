package com.rodrilang.librarymanager.integrations.tiendanube.management.dto.request;

import com.rodrilang.librarymanager.integrations.tiendanube.management.enums.TiendanubeBulkAction;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record TiendanubeBulkOperationRequest(
        @NotNull TiendanubeBulkAction action,
        @Valid @NotNull TiendanubeBulkSelectionRequest selection
) {
}
