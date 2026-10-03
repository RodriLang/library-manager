package com.rodrilang.librarymanager.purchasing.preference.service;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.model.Bookstore;
import com.rodrilang.librarymanager.provider.catalog.repository.ProviderBookRepository;
import com.rodrilang.librarymanager.provider.model.Provider;
import com.rodrilang.librarymanager.provider.repository.ProviderRepository;
import com.rodrilang.librarymanager.purchasing.preference.dto.response.PreferredProviderResponse;
import com.rodrilang.librarymanager.purchasing.preference.model.BookstoreBookProviderPreference;
import com.rodrilang.librarymanager.purchasing.preference.model.ProviderPreferenceSource;
import com.rodrilang.librarymanager.purchasing.preference.repository.BookstoreBookProviderPreferenceRepository;
import com.rodrilang.librarymanager.purchasing.requirement.model.PurchaseRequirementStatus;
import com.rodrilang.librarymanager.purchasing.requirement.repository.PurchaseRequirementRepository;
import com.rodrilang.librarymanager.service.BookService;
import com.rodrilang.librarymanager.service.BookstoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ProviderPreferenceService {

    private final BookstoreBookProviderPreferenceRepository repository;
    private final PurchaseRequirementRepository requirementRepository;
    private final ProviderRepository providerRepository;
    private final ProviderBookRepository providerBookRepository;
    private final BookService bookService;
    private final BookstoreService bookstoreService;
    private final BookstoreContext bookstoreContext;

    @Transactional(readOnly = true)
    public PreferredProviderResponse findForCurrentBookstore(Long bookId) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        return repository.findByBookstoreIdAndBookId(bookstoreId, bookId)
                .map(this::toResponse)
                .orElseGet(PreferredProviderResponse::empty);
    }

    @Transactional(readOnly = true)
    public Provider findPreferredProviderEntity(Long bookstoreId, Long bookId) {
        return repository.findByBookstoreIdAndBookId(bookstoreId, bookId)
                .map(BookstoreBookProviderPreference::getProvider)
                .filter(Provider::isPurchasable)
                .filter(provider -> providerBookRepository.existsByProviderIdAndBookIdAndActiveTrue(
                        provider.getId(),
                        bookId
                ))
                .orElse(null);
    }

    @Transactional
    public PreferredProviderResponse setManual(Long bookId, Long providerId) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        Provider provider = requireAvailableProvider(providerId, bookId);
        Book book = bookService.getEntityById(bookId);
        Bookstore bookstore = bookstoreService.getEntityById(bookstoreId);

        BookstoreBookProviderPreference preference = repository.findForUpdate(bookstoreId, bookId)
                .orElseGet(() -> BookstoreBookProviderPreference.builder()
                        .bookstore(bookstore)
                        .book(book)
                        .build());

        preference.setProvider(provider);
        preference.setSource(ProviderPreferenceSource.MANUAL);
        preference = repository.save(preference);

        syncPendingRequirement(bookstoreId, bookId, provider);

        return toResponse(preference);
    }

    @Transactional
    public void clearManual(Long bookId) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        repository.findForUpdate(bookstoreId, bookId).ifPresent(repository::delete);
        syncPendingRequirement(bookstoreId, bookId, null);
    }

    @Transactional
    public void rememberLastUsed(Long bookstoreId, Long bookId, Long providerId, Instant usedAt) {
        Provider provider = providerRepository.findById(providerId)
                .filter(Provider::isPurchasable)
                .orElse(null);
        if (provider == null) {
            return;
        }

        BookstoreBookProviderPreference preference = repository.findForUpdate(bookstoreId, bookId)
                .orElse(null);

        if (preference != null && preference.getSource() == ProviderPreferenceSource.MANUAL) {
            return;
        }

        if (preference == null) {
            Book book = bookService.getEntityById(bookId);
            Bookstore bookstore = bookstoreService.getEntityById(bookstoreId);
            preference = BookstoreBookProviderPreference.builder()
                    .bookstore(bookstore)
                    .book(book)
                    .build();
        }

        preference.setProvider(provider);
        preference.setSource(ProviderPreferenceSource.LAST_ORDER);
        preference.setLastUsedAt(usedAt != null ? usedAt : Instant.now());
        repository.save(preference);

        syncPendingRequirement(bookstoreId, bookId, provider);
    }

    private void syncPendingRequirement(Long bookstoreId, Long bookId, Provider provider) {
        requirementRepository.findByBookstoreAndBookAndStatusForUpdate(
                        bookstoreId,
                        bookId,
                        PurchaseRequirementStatus.PENDING
                )
                .ifPresent(requirement -> requirement.setPreferredProvider(provider));
    }

    private Provider requireAvailableProvider(Long providerId, Long bookId) {
        Provider provider = providerRepository.findById(providerId)
                .filter(Provider::isPurchasable)
                .orElseThrow(() -> new BusinessException("El proveedor seleccionado no se encuentra activo."));

        if (!providerBookRepository.existsByProviderIdAndBookIdAndActiveTrue(providerId, bookId)) {
            throw new BusinessException("El proveedor seleccionado no comercializa este libro.");
        }

        return provider;
    }

    private PreferredProviderResponse toResponse(BookstoreBookProviderPreference preference) {
        Provider provider = preference.getProvider();
        boolean available = provider != null
                && provider.isPurchasable()
                && providerBookRepository.existsByProviderIdAndBookIdAndActiveTrue(
                provider.getId(),
                preference.getBook().getId()
        );

        return new PreferredProviderResponse(
                provider != null ? provider.getId() : null,
                provider != null ? provider.getName() : null,
                preference.getSource(),
                preference.getLastUsedAt(),
                available
        );
    }
}
