package com.rodrilang.librarymanager.inventory.image.dto;

import java.util.List;

public record InventoryImageGalleryResponse(
        int maxImages,
        List<InventoryImageResponse> images
) {
}
