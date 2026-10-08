package com.rodrilang.librarymanager.store.dto;
import com.rodrilang.librarymanager.store.model.StoreTitleFormat;
import jakarta.validation.constraints.*;
public record StoreSettingsRequest(
        @NotBlank @Size(max=150) String displayName,
        @NotNull StoreTitleFormat titleFormat,
        @NotNull Boolean showIsbn,
        @NotNull Boolean showAuthor,
        @NotNull Boolean showPublisher,
        @NotNull Boolean showStock,
        @Size(max=1000) String logoUrl,
        @Size(max=1000) String faviconUrl,
        @Size(max=20) String primaryColor,
        @Size(max=20) String secondaryColor,
        String description
) {}
