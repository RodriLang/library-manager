package com.rodrilang.librarymanager.purchasing.service;

import com.rodrilang.librarymanager.provider.model.Provider;
import com.rodrilang.librarymanager.provider.service.ProviderAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProviderResolver {

    private final ProviderAccessService providerAccessService;

    public Provider requirePurchasable(Long providerId) {
        return providerAccessService.requireUsableByCurrentBookstore(providerId);
    }
}
