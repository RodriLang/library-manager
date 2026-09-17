package com.rodrilang.librarymanager.provider.dto.response;

import com.rodrilang.librarymanager.provider.model.ProviderType;

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
        boolean purchasable

) {
}
