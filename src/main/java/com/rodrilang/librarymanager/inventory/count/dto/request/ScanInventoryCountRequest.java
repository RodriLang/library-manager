package com.rodrilang.librarymanager.inventory.count.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ScanInventoryCountRequest(

        @NotBlank
        String code

) {
}
