package com.rodrilang.librarymanager.store.dto;
import com.rodrilang.librarymanager.store.model.StoreSocialLinkType;
import jakarta.validation.constraints.*;
public record StoreSocialLinkRequest(@NotNull StoreSocialLinkType type, @Size(max=1000) String url, Boolean enabled, @Min(0) Integer displayOrder) {}
