package com.rodrilang.librarymanager.purchasing.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ScanPurchaseItemRequest(
        @NotBlank String code
) {
}
