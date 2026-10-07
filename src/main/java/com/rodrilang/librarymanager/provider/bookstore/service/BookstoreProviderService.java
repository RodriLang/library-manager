package com.rodrilang.librarymanager.provider.bookstore.service;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.inventory.pricing.repository.BookstorePriceListFormatRepository;
import com.rodrilang.librarymanager.model.Bookstore;
import com.rodrilang.librarymanager.provider.bookstore.dto.BookstoreProviderResponse;
import com.rodrilang.librarymanager.provider.bookstore.dto.CreateBookstoreProviderRequest;
import com.rodrilang.librarymanager.provider.bookstore.dto.UpdateBookstoreProviderRequest;
import com.rodrilang.librarymanager.provider.bookstore.model.BookstoreProvider;
import com.rodrilang.librarymanager.provider.bookstore.repository.BookstoreProviderRepository;
import com.rodrilang.librarymanager.provider.model.Provider;
import com.rodrilang.librarymanager.provider.model.ProviderSource;
import com.rodrilang.librarymanager.provider.model.ProviderType;
import com.rodrilang.librarymanager.provider.model.ProviderVerificationStatus;
import com.rodrilang.librarymanager.provider.repository.ProviderRepository;
import com.rodrilang.librarymanager.provider.service.ProviderAccessService;
import com.rodrilang.librarymanager.repository.BookstoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class BookstoreProviderService {

    private final BookstoreContext bookstoreContext;
    private final BookstoreProviderRepository repository;
    private final ProviderRepository providerRepository;
    private final BookstoreRepository bookstoreRepository;
    private final ProviderAccessService providerAccessService;
    private final BookstorePriceListFormatRepository priceListFormatRepository;

    @Transactional(readOnly = true)
    public List<BookstoreProviderResponse> findMine() {
        return repository
                .findAllByBookstoreIdAndActiveTrueOrderByProviderNameAsc(bookstoreContext.getCurrentBookstoreId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public BookstoreProviderResponse create(CreateBookstoreProviderRequest request) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        Long userId = bookstoreContext.getCurrentUserId();
        Bookstore bookstore = bookstoreRepository.findById(bookstoreId)
                .orElseThrow(() -> new BusinessException("No se encontró la librería."));

        String taxId = clean(request.taxId());
        if (taxId != null) {
            Provider existing = providerRepository
                    .findFirstByActiveTrueAndTypeAndVerificationStatusAndTaxIdIgnoreCase(
                            ProviderType.COMMERCIAL,
                            ProviderVerificationStatus.VERIFIED,
                            taxId
                    )
                    .orElseGet(() -> providerRepository
                            .findFirstByActiveTrueAndTypeAndVerificationStatusAndCreatedByBookstoreIdAndTaxIdIgnoreCase(
                                    ProviderType.COMMERCIAL,
                                    ProviderVerificationStatus.PENDING_REVIEW,
                                    bookstoreId,
                                    taxId
                            )
                            .orElse(null));
            if (existing != null) {
                return toResponse(ensureRelation(bookstore, existing, request.notes()));
            }
        }

        Instant now = Instant.now();
        Provider provider = Provider.builder()
                .code(generateUniqueCode(request.name()))
                .name(request.name().trim())
                .type(ProviderType.COMMERCIAL)
                .taxId(taxId)
                .email(clean(request.email()))
                .phone(clean(request.phone()))
                .verificationStatus(ProviderVerificationStatus.PENDING_REVIEW)
                .source(ProviderSource.BOOKSTORE)
                .createdByBookstoreId(bookstoreId)
                .createdByUserId(userId)
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .build();
        provider = providerRepository.save(provider);

        return toResponse(ensureRelation(bookstore, provider, request.notes()));
    }

    @Transactional
    public BookstoreProviderResponse update(Long providerId, UpdateBookstoreProviderRequest request) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        Provider provider = providerAccessService.requireUsableByBookstore(providerId, bookstoreId);

        BookstoreProvider relation = repository.findByBookstoreIdAndProviderId(bookstoreId, providerId)
                .orElseGet(() -> {
                    Bookstore bookstore = bookstoreRepository.findById(bookstoreId)
                            .orElseThrow(() -> new BusinessException("No se encontró la librería."));
                    return BookstoreProvider.builder()
                            .bookstore(bookstore)
                            .provider(provider)
                            .active(true)
                            .build();
                });

        if (request.active() != null) relation.setActive(request.active());
        if (request.preferred() != null) relation.setPreferred(request.preferred());
        relation.setNotes(clean(request.notes()));
        return toResponse(repository.save(relation));
    }

    @Transactional
    public void withdraw(Long providerId) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();

        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new BusinessException("No se encontró el proveedor solicitado."));

        if (provider.getVerificationStatus() != ProviderVerificationStatus.PENDING_REVIEW
                || provider.getSource() != ProviderSource.BOOKSTORE
                || !bookstoreId.equals(provider.getCreatedByBookstoreId())) {
            throw new BusinessException(
                    "Sólo podés eliminar proveedores creados por tu librería que todavía estén pendientes de incorporación a Anaquel."
            );
        }

        BookstoreProvider relation = repository.findByBookstoreIdAndProviderId(bookstoreId, providerId)
                .orElseThrow(() -> new BusinessException(
                        "No se encontró la relación de este proveedor con tu librería."
                ));

        if (relation.isActive()) {
            throw new BusinessException(
                    "Primero quitá el proveedor de Mis proveedores y luego podrás eliminarlo."
            );
        }

        Instant now = Instant.now();
        provider.setVerificationStatus(ProviderVerificationStatus.WITHDRAWN);
        provider.setActive(false);
        provider.setUpdatedAt(now);
        providerRepository.save(provider);

        relation.setPreferred(false);
        repository.save(relation);

        priceListFormatRepository.deactivateAllByBookstoreIdAndProviderId(bookstoreId, providerId);
    }

    private BookstoreProvider ensureRelation(Bookstore bookstore, Provider provider, String notes) {
        BookstoreProvider relation = repository
                .findByBookstoreIdAndProviderId(bookstore.getId(), provider.getId())
                .orElseGet(() -> BookstoreProvider.builder()
                        .bookstore(bookstore)
                        .provider(provider)
                        .active(true)
                        .build());
        relation.setActive(true);
        if (notes != null && !notes.isBlank()) {
            relation.setNotes(notes.trim());
        }
        return repository.save(relation);
    }

    private BookstoreProviderResponse toResponse(BookstoreProvider bp) {
        Provider provider = bp.getProvider();
        return new BookstoreProviderResponse(
                provider.getId(),
                provider.getCode(),
                provider.getName(),
                provider.getTaxId(),
                provider.getEmail(),
                provider.getPhone(),
                provider.getVerificationStatus(),
                provider.getSource(),
                provider.getVerificationStatus() == ProviderVerificationStatus.PENDING_REVIEW,
                bp.isActive(),
                bp.isPreferred(),
                bp.getNotes()
        );
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String generateUniqueCode(String name) {
        String ascii = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        String base = ascii
                .trim()
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (base.isBlank()) {
            base = "PROVEEDOR";
        }
        if (base.length() > 42) {
            base = base.substring(0, 42).replaceAll("_+$", "");
        }

        String candidate = base;
        int suffix = 2;
        while (providerRepository.existsByCodeIgnoreCase(candidate)) {
            String suffixText = "_" + suffix++;
            int maxBaseLength = 50 - suffixText.length();
            candidate = base.substring(0, Math.min(base.length(), maxBaseLength)) + suffixText;
        }
        return candidate;
    }
}
