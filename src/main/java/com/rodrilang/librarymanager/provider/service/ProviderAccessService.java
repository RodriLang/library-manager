package com.rodrilang.librarymanager.provider.service;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.provider.model.Provider;
import com.rodrilang.librarymanager.provider.model.ProviderType;
import com.rodrilang.librarymanager.provider.model.ProviderVerificationStatus;
import com.rodrilang.librarymanager.provider.repository.ProviderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProviderAccessService {

    private final ProviderRepository providerRepository;
    private final BookstoreContext bookstoreContext;

    @Transactional(readOnly = true)
    public Provider requireUsableByCurrentBookstore(Long providerId) {
        return requireUsableByBookstore(providerId, bookstoreContext.getCurrentBookstoreId());
    }

    @Transactional(readOnly = true)
    public Provider requireUsableByBookstore(Long providerId, Long bookstoreId) {
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new BusinessException("No se encontró el proveedor seleccionado."));

        if (!provider.isActive() || provider.getType() != ProviderType.COMMERCIAL) {
            throw new BusinessException("El proveedor seleccionado no se encuentra disponible.");
        }

        if (provider.getVerificationStatus() == ProviderVerificationStatus.VERIFIED) {
            return provider;
        }

        if (provider.getVerificationStatus() == ProviderVerificationStatus.PENDING_REVIEW
                && bookstoreId != null
                && bookstoreId.equals(provider.getCreatedByBookstoreId())) {
            return provider;
        }

        throw new BusinessException("El proveedor seleccionado no se encuentra disponible para esta librería.");
    }

    public boolean isVisibleToBookstore(Provider provider, Long bookstoreId) {
        if (!provider.isActive()) {
            return false;
        }
        if (provider.getVerificationStatus() == ProviderVerificationStatus.VERIFIED) {
            return true;
        }
        return provider.getVerificationStatus() == ProviderVerificationStatus.PENDING_REVIEW
                && bookstoreId != null
                && bookstoreId.equals(provider.getCreatedByBookstoreId());
    }

    public boolean isUsableByBookstore(Provider provider, Long bookstoreId) {
        return provider != null
                && provider.getType() == ProviderType.COMMERCIAL
                && provider.isPurchasable()
                && isVisibleToBookstore(provider, bookstoreId);
    }
}
