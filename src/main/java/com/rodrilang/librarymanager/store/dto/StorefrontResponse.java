package com.rodrilang.librarymanager.store.dto;

import com.rodrilang.librarymanager.store.model.*;
import java.util.List;
import java.util.UUID;

public record StorefrontResponse(
        UUID publicId,
        String slug,
        String displayName,
        String description,
        String logoUrl,
        String faviconUrl,
        String primaryColor,
        String secondaryColor,
        StoreTitleFormat titleFormat,
        boolean showIsbn,
        boolean showAuthor,
        boolean showPublisher,
        boolean showStock,
        String whatsappPhone,
        boolean mercadoPagoEnabled,
        List<StorefrontSocialLinkResponse> socialLinks,

        boolean headerShowLogo,
        boolean headerShowName,
        StoreHeaderBrandLayout headerBrandLayout,
        StoreHeaderLogoSize headerLogoSize,

        boolean heroShowEyebrow,
        String heroEyebrow,
        boolean heroShowLogo,
        boolean heroShowName,
        String heroTitle,
        String heroDescription,
        StoreHeroTextAlignment heroTextAlignment,
        boolean heroShowBooks,
        StoreHeroBooksMode heroBooksMode,
        List<StorefrontProductResponse> heroProducts,
        boolean heroPrimaryButtonEnabled,
        String heroPrimaryButtonText,
        boolean heroSecondaryButtonEnabled,
        String heroSecondaryButtonText,

        boolean discoverEnabled,
        String discoverEyebrow,
        String discoverTitle,
        String discoverDescription,
        String discoverButtonText
) {}
