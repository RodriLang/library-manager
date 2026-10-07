package com.rodrilang.librarymanager.provider.service;

import com.rodrilang.librarymanager.provider.dto.response.ProviderResponse;
import com.rodrilang.librarymanager.provider.model.Provider;
import org.springframework.stereotype.Component;

@Component
public class ProviderResponseMapper {

    public ProviderResponse toResponse(Provider provider) {
        return new ProviderResponse(
                provider.getId(),
                provider.getCode(),
                provider.getName(),
                provider.getType(),
                provider.getTaxId(),
                provider.getEmail(),
                provider.getPhone(),
                provider.getNotes(),
                provider.isActive(),
                provider.isPurchasable(),
                provider.getVerificationStatus(),
                provider.getSource(),
                provider.getCreatedByBookstoreId(),
                provider.getCreatedByUserId(),
                provider.getReviewedAt(),
                provider.getReviewedByUserId()
        );
    }
}
