package com.rodrilang.librarymanager.store.dto;
import jakarta.validation.constraints.*;
public record UpdateStoreSectionRequest(
        @Size(max=150) String name,
        Boolean enabled,
        @Min(0) Integer displayOrder,
        @Min(1) @Max(365) Integer windowDays,
        @Min(1) @Max(50) Integer maxItems
) {}
