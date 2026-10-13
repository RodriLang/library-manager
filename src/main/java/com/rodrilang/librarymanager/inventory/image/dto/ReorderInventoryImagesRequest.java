package com.rodrilang.librarymanager.inventory.image.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ReorderInventoryImagesRequest(
        @NotNull
        @Size(max = 5)
        List<Long> imageIds
) {
}
