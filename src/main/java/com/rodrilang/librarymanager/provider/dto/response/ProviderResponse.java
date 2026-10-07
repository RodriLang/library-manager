package com.rodrilang.librarymanager.provider.dto.response;

import com.rodrilang.librarymanager.provider.model.ProviderSource;
import com.rodrilang.librarymanager.provider.model.ProviderType;
import com.rodrilang.librarymanager.provider.model.ProviderVerificationStatus;

import java.time.Instant;

public record ProviderResponse(
        Long id,
        String code,
        String name,
        ProviderType type,
        String taxId,
        String email,
        String phone,
        String notes,
        boolean active,
        boolean purchasable,
        ProviderVerificationStatus verificationStatus,
        ProviderSource source,
        Long createdByBookstoreId,
        Long createdByUserId,
        Instant reviewedAt,
        Long reviewedByUserId
) {
}
