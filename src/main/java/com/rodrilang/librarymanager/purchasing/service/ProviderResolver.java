package com.rodrilang.librarymanager.purchasing.service;

import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.provider.model.Provider;
import com.rodrilang.librarymanager.provider.repository.ProviderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProviderResolver {

    private final ProviderRepository providerRepository;

    public Provider requirePurchasable(Long providerId) {
        return providerRepository.findById(providerId)
                .filter(Provider::isPurchasable)
                .orElseThrow(() -> new BusinessException("Proveedor no encontrado, inactivo o no disponible para compras"));
    }
}
