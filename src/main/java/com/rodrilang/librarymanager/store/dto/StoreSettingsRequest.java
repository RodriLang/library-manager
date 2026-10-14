package com.rodrilang.librarymanager.store.dto;

import com.rodrilang.librarymanager.store.model.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

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
        @Size(max=50) String phone,
        String description,
        @Valid List<StoreSocialLinkRequest> socialLinks,

        Boolean headerShowLogo,
        Boolean headerShowName,
        StoreHeaderBrandLayout headerBrandLayout,
        StoreHeaderLogoSize headerLogoSize,

        Boolean heroShowEyebrow,
        @Size(max=120) String heroEyebrow,
        Boolean heroShowLogo,
        Boolean heroShowName,
        @Size(max=220) String heroTitle,
        String heroDescription,
        StoreHeroTextAlignment heroTextAlignment,
        Boolean heroShowBooks,
        StoreHeroBooksMode heroBooksMode,
        @Size(max=3) List<Long> heroItemInventoryIds,
        Boolean heroPrimaryButtonEnabled,
        @Size(max=80) String heroPrimaryButtonText,
        Boolean heroSecondaryButtonEnabled,
        @Size(max=80) String heroSecondaryButtonText,

        Boolean discoverEnabled,
        @Size(max=120) String discoverEyebrow,
        @Size(max=220) String discoverTitle,
        String discoverDescription,
        @Size(max=80) String discoverButtonText
) {}
