package com.rodrilang.librarymanager.inventory.image.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddInventoryImageUrlRequest(
        @NotBlank
        @Size(max = 2048)
        String url
) {
}
