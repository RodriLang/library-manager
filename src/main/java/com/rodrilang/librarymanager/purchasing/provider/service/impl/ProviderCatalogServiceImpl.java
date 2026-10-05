package com.rodrilang.librarymanager.purchasing.provider.service.impl;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.enums.BookCondition;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.provider.model.Provider;
import com.rodrilang.librarymanager.provider.model.ProviderType;
import com.rodrilang.librarymanager.provider.catalog.model.ProviderBook;
import com.rodrilang.librarymanager.provider.repository.ProviderRepository;
import com.rodrilang.librarymanager.provider.catalog.repository.ProviderBookRepository;
import com.rodrilang.librarymanager.model.Inventory;
import com.rodrilang.librarymanager.purchasing.provider.dto.ProviderCatalogFilter;
import com.rodrilang.librarymanager.purchasing.provider.dto.response.ProviderCatalogAlternativeResponse;
import com.rodrilang.librarymanager.purchasing.provider.dto.response.ProviderCatalogBookResponse;
import com.rodrilang.librarymanager.purchasing.provider.repository.ProviderBookSpecifications;
import com.rodrilang.librarymanager.purchasing.provider.repository.projection.BookAlternativeProviderProjection;
import com.rodrilang.librarymanager.purchasing.provider.service.ProviderCatalogService;
import com.rodrilang.librarymanager.purchasing.model.BookstoreProviderBookTerm;
import com.rodrilang.librarymanager.purchasing.repository.BookstoreProviderBookTermRepository;
import com.rodrilang.librarymanager.purchasing.requirement.model.PurchaseRequirement;
import com.rodrilang.librarymanager.purchasing.requirement.model.PurchaseRequirementStatus;
import com.rodrilang.librarymanager.purchasing.requirement.repository.PurchaseRequirementRepository;
import com.rodrilang.librarymanager.repository.BookRepository;
import com.rodrilang.librarymanager.repository.InventoryRepository;
import com.rodrilang.librarymanager.inventory.pricing.service.InventoryPriceService;
import com.rodrilang.librarymanager.repository.projection.BookAuthorNameProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProviderCatalogServiceImpl
        implements ProviderCatalogService {

    private final ProviderBookRepository providerBookRepository;
    private final ProviderRepository providerRepository;
    private final BookRepository bookRepository;

    private final InventoryPriceService inventoryPriceService;
    private final InventoryRepository inventoryRepository;
    private final PurchaseRequirementRepository requirementRepository;
    private final BookstoreProviderBookTermRepository providerBookTermRepository;

    private final BookstoreContext bookstoreContext;

    @Override
    public Page<ProviderCatalogBookResponse> findAll(
            Long providerId,
            ProviderCatalogFilter filter,
            Pageable pageable
    ) {

        validateProvider(providerId);

        Specification<ProviderBook> specification =
                Specification.allOf(
                        ProviderBookSpecifications.providerId(providerId),
                        ProviderBookSpecifications.active(),
                        ProviderBookSpecifications.activeBook(),
                        ProviderBookSpecifications.search(filter.query())
                );

        Page<ProviderBook> page =
                providerBookRepository.findAll(
                        specification,
                        pageable
                );

        if (page.isEmpty()) {
            return Page.empty(pageable);
        }

        Long bookstoreId =
                bookstoreContext.getCurrentBookstoreId();

        List<Long> bookIds =
                page.getContent()
                        .stream()
                        .map(providerBook ->
                                providerBook.getBook().getId()
                        )
                        .toList();

        Map<Long, Inventory> inventoryByBookId =
                loadInventory(
                        bookstoreId,
                        bookIds
                );

        Map<Long, PurchaseRequirement> requirementByBookId =
                loadRequirements(
                        bookstoreId,
                        bookIds
                );

        Map<Long, BookstoreProviderBookTerm> termsByBookId =
                providerBookTermRepository
                        .findAllByBookstoreIdAndProviderIdAndBookIdIn(bookstoreId, providerId, bookIds)
                        .stream()
                        .collect(Collectors.toMap(
                                term -> term.getBook().getId(),
                                Function.identity()
                        ));

        Map<Long, List<ProviderCatalogAlternativeResponse>>
                alternativesByBookId =
                loadAlternatives(
                        bookstoreId,
                        providerId,
                        bookIds
                );

        Map<Long, List<String>> authorsByBookId =
                bookRepository
                        .findAuthorNamesByBookIds(bookIds)
                        .stream()
                        .collect(
                                Collectors.groupingBy(
                                        BookAuthorNameProjection::getBookId,
                                        Collectors.mapping(
                                                BookAuthorNameProjection::getAuthorName,
                                                Collectors.toList()
                                        )
                                )
                        );

        return page.map(providerBook ->
                toResponse(
                        providerBook,
                        inventoryByBookId,
                        requirementByBookId,
                        alternativesByBookId,
                        authorsByBookId,
                        termsByBookId
                )
        );
    }

    private Map<Long, Inventory> loadInventory(
            Long bookstoreId,
            List<Long> bookIds
    ) {

        return inventoryRepository
                .findAllByBookstoreIdAndBookIdInAndConditionAndActiveTrue(
                        bookstoreId,
                        bookIds,
                        BookCondition.NEW
                )
                .stream()
                .collect(
                        Collectors.toMap(
                                inventory ->
                                        inventory.getBook().getId(),
                                Function.identity()
                        )
                );
    }

    private Map<Long, PurchaseRequirement> loadRequirements(
            Long bookstoreId,
            List<Long> bookIds
    ) {

        return requirementRepository
                .findByBookstoreAndBookIdsAndStatus(
                        bookstoreId,
                        bookIds,
                        PurchaseRequirementStatus.PENDING
                )
                .stream()
                .collect(
                        Collectors.toMap(
                                requirement ->
                                        requirement.getBook().getId(),
                                Function.identity()
                        )
                );
    }

    private Map<Long, List<ProviderCatalogAlternativeResponse>>
    loadAlternatives(
            Long bookstoreId,
            Long providerId,
            List<Long> bookIds
    ) {

        List<BookAlternativeProviderProjection> providers =
                providerBookRepository
                        .findAlternativeProviders(
                                bookIds,
                                providerId,
                                ProviderType.COMMERCIAL
                        );

        if (providers.isEmpty()) {
            return Map.of();
        }

        Map<ProviderBookKey, BigDecimal> pricesByProviderBook =
                providerBookTermRepository
                        .findAllByBookstoreIdAndBookIdIn(bookstoreId, bookIds)
                        .stream()
                        .filter(term -> term.getLatestListPrice() != null)
                        .collect(Collectors.toMap(
                                term -> new ProviderBookKey(
                                        term.getProvider().getId(),
                                        term.getBook().getId()
                                ),
                                BookstoreProviderBookTerm::getLatestListPrice,
                                (left, right) -> right
                        ));

        return providers.stream()
                .collect(
                        Collectors.groupingBy(
                                BookAlternativeProviderProjection::getBookId,
                                Collectors.mapping(
                                        provider ->
                                                new ProviderCatalogAlternativeResponse(
                                                        provider.getProviderId(),
                                                        provider.getProviderName(),
                                                        pricesByProviderBook.get(
                                                                new ProviderBookKey(
                                                                        provider.getProviderId(),
                                                                        provider.getBookId()
                                                                )
                                                        )
                                                ),
                                        Collectors.toList()
                                )
                        )
                );
    }

    private ProviderCatalogBookResponse toResponse(
            ProviderBook providerBook,
            Map<Long, Inventory> inventoryByBookId,
            Map<Long, PurchaseRequirement> requirementByBookId,
            Map<Long, List<ProviderCatalogAlternativeResponse>> alternativesByBookId,
            Map<Long, List<String>> authorsByBookId,
            Map<Long, BookstoreProviderBookTerm> termsByBookId
    ) {

        var book = providerBook.getBook();

        Long bookId = book.getId();

        Inventory inventory = inventoryByBookId.get(bookId);
        BigDecimal salePrice = inventory != null
                ? inventoryPriceService.currentAmount(inventory.getId())
                : null;

        BookstoreProviderBookTerm providerTerm = termsByBookId.get(bookId);
        BigDecimal providerPrice = providerTerm != null
                ? providerTerm.getLatestListPrice()
                : null;

        PurchaseRequirement requirement = requirementByBookId.get(bookId);

        return new ProviderCatalogBookResponse(
                providerBook.getId(),
                bookId,
                book.getPreferredIsbn(),
                book.getTitle(),


                authorsByBookId.getOrDefault(
                        bookId,
                        List.of()
                ),

                book.getPublisher() != null
                        ? book.getPublisher().getName()
                        : null,

                book.getCoverUrl(),

                providerBook.getExternalCode(),

                providerBook.getFirstSeenAt(),
                providerBook.getLastSeenAt(),
                providerBook.getSource(),
                providerBook.getVerificationStatus(),

                providerPrice,
                providerTerm != null ? providerTerm.getLatestListEffectiveFrom() : null,
                providerTerm != null ? providerTerm.getLastSeenInPriceListAt() : null,
                providerTerm != null && providerTerm.getLastPriceImport() != null
                        ? providerTerm.getLastPriceImport().getId()
                        : null,

                salePrice,

                inventory != null
                        ? inventory.getId()
                        : null,

                inventory != null
                        ? inventory.getStock()
                        : null,

                inventory != null
                        ? inventory.getMinimumStock()
                        : null,

                requirement != null
                        ? requirement.getId()
                        : null,

                requirement != null
                        ? requirement.getQuantity()
                        : null,

                requirement != null
                        && requirement.getPreferredProvider() != null
                        ? requirement.getPreferredProvider().getId()
                        : null,

                requirement != null
                        && requirement.getPreferredProvider() != null
                        ? requirement.getPreferredProvider().getName()
                        : null,

                alternativesByBookId.getOrDefault(
                        bookId,
                        List.of()
                )
        );
    }

    private record ProviderBookKey(Long providerId, Long bookId) {
    }

    private void validateProvider(Long providerId) {

        providerRepository
                .findById(providerId)
                .filter(Provider::isPurchasable)
                .orElseThrow(() ->
                        new BusinessException(
                                "El proveedor seleccionado no se encuentra activo."
                        )
                );
    }
}