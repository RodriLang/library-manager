package com.rodrilang.librarymanager.provider.bookstore.dto;

import com.rodrilang.librarymanager.provider.model.ProviderSource;
import com.rodrilang.librarymanager.provider.model.ProviderVerificationStatus;

public record BookstoreProviderResponse(
        Long providerId,
        String code,
        String name,
        String taxId,
        String email,
        String phone,
        ProviderVerificationStatus verificationStatus,
        ProviderSource source,
        boolean pendingReview,
        boolean active,
        boolean preferred,
        String notes
) {
}
