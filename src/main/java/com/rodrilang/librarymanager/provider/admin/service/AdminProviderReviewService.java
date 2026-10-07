package com.rodrilang.librarymanager.provider.admin.service;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.dto.response.PageResponse;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.inventory.pricing.repository.BookstorePriceListFormatRepository;
import com.rodrilang.librarymanager.provider.admin.dto.ProviderReviewDecision;
import com.rodrilang.librarymanager.provider.admin.dto.ReviewProviderRequest;
import com.rodrilang.librarymanager.provider.bookstore.repository.BookstoreProviderRepository;
import com.rodrilang.librarymanager.provider.dto.response.ProviderResponse;
import com.rodrilang.librarymanager.provider.model.Provider;
import com.rodrilang.librarymanager.provider.model.ProviderVerificationStatus;
import com.rodrilang.librarymanager.provider.model.ProviderType;
import com.rodrilang.librarymanager.provider.repository.ProviderRepository;
import com.rodrilang.librarymanager.provider.service.ProviderResponseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AdminProviderReviewService {

    private final ProviderRepository providerRepository;
    private final BookstoreProviderRepository bookstoreProviderRepository;
    private final BookstorePriceListFormatRepository priceListFormatRepository;
    private final ProviderResponseMapper responseMapper;
    private final BookstoreContext bookstoreContext;

    @Transactional(readOnly = true)
    public PageResponse<ProviderResponse> list(ProviderVerificationStatus status, Pageable pageable) {
        return PageResponse.of(providerRepository.findForAdminReview(ProviderType.COMMERCIAL, status, pageable).map(responseMapper::toResponse));
    }

    @Transactional
    public ProviderResponse review(Long providerId, ReviewProviderRequest request) {
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new BusinessException("No se encontró el proveedor solicitado."));

        if (provider.getVerificationStatus() != ProviderVerificationStatus.PENDING_REVIEW) {
            throw new BusinessException("El proveedor ya fue revisado.");
        }

        Instant now = Instant.now();
        provider.setReviewedAt(now);
        provider.setReviewedByUserId(bookstoreContext.getCurrentUserId());
        provider.setUpdatedAt(now);

        if (request.decision() == ProviderReviewDecision.APPROVE) {
            provider.setVerificationStatus(ProviderVerificationStatus.VERIFIED);
            provider.setActive(true);
        } else {
            provider.setVerificationStatus(ProviderVerificationStatus.REJECTED);
            provider.setActive(false);
            bookstoreProviderRepository.deactivateAllByProviderId(providerId);
            priceListFormatRepository.deactivateAllByProviderId(providerId);
        }

        return responseMapper.toResponse(providerRepository.save(provider));
    }
}
