package com.rodrilang.librarymanager.purchasing.provider.service.impl;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.enums.BookCondition;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.importer.price.configuration.model.PriceListProvider;
import com.rodrilang.librarymanager.importer.price.configuration.model.ProviderBook;
import com.rodrilang.librarymanager.importer.price.configuration.repository.PriceListProviderRepository;
import com.rodrilang.librarymanager.importer.price.configuration.repository.ProviderBookRepository;
import com.rodrilang.librarymanager.model.EditorialPrice;
import com.rodrilang.librarymanager.model.Inventory;
import com.rodrilang.librarymanager.purchasing.provider.dto.ProviderCatalogFilter;
import com.rodrilang.librarymanager.purchasing.provider.dto.response.ProviderCatalogAlternativeResponse;
import com.rodrilang.librarymanager.purchasing.provider.dto.response.ProviderCatalogBookResponse;
import com.rodrilang.librarymanager.purchasing.provider.repository.ProviderBookSpecifications;
import com.rodrilang.librarymanager.purchasing.provider.repository.projection.BookAlternativeProviderProjection;
import com.rodrilang.librarymanager.purchasing.provider.service.ProviderCatalogService;
import com.rodrilang.librarymanager.purchasing.requirement.model.PurchaseRequirement;
import com.rodrilang.librarymanager.purchasing.requirement.model.PurchaseRequirementStatus;
import com.rodrilang.librarymanager.purchasing.requirement.repository.PurchaseRequirementRepository;
import com.rodrilang.librarymanager.repository.EditorialPriceRepository;
import com.rodrilang.librarymanager.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProviderCatalogServiceImpl
        implements ProviderCatalogService {

    private final ProviderBookRepository providerBookRepository;
    private final PriceListProviderRepository providerRepository;

    private final EditorialPriceRepository editorialPriceRepository;
    private final InventoryRepository inventoryRepository;
    private final PurchaseRequirementRepository requirementRepository;

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

        Map<Long, EditorialPrice> priceByBookId =
                loadProviderPrices(
                        providerId,
                        bookIds
                );

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

        Map<Long, List<ProviderCatalogAlternativeResponse>>
                alternativesByBookId =
                loadAlternatives(
                        providerId,
                        bookIds
                );

        return page.map(providerBook ->
                toResponse(
                        providerBook,
                        priceByBookId,
                        inventoryByBookId,
                        requirementByBookId,
                        alternativesByBookId
                )
        );
    }

    private Map<Long, EditorialPrice> loadProviderPrices(
            Long providerId,
            List<Long> bookIds
    ) {

        return editorialPriceRepository
                .findCurrentByProviderAndBookIds(
                        providerId,
                        bookIds,
                        LocalDate.now(ZoneId.systemDefault())
                )
                .stream()
                .collect(
                        Collectors.toMap(
                                price -> price.getBook().getId(),
                                Function.identity()
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
            Long providerId,
            List<Long> bookIds
    ) {

        List<BookAlternativeProviderProjection> providers =
                providerBookRepository
                        .findAlternativeProviders(
                                bookIds,
                                providerId
                        );

        if (providers.isEmpty()) {
            return Map.of();
        }

        Set<Long> providerIds =
                providers.stream()
                        .map(
                                BookAlternativeProviderProjection::getProviderId
                        )
                        .collect(Collectors.toSet());

        Map<String, BigDecimal> prices =
                editorialPriceRepository
                        .findCurrentByBooksAndProviders(
                                bookIds,
                                providerIds,
                                LocalDate.now(ZoneId.systemDefault())
                        )
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        price ->
                                                price.getBook().getId()
                                                        + ":"
                                                        + price.getProvider().getId(),
                                        EditorialPrice::getPrice
                                )
                        );

        return providers.stream()
                .collect(
                        Collectors.groupingBy(
                                BookAlternativeProviderProjection::getBookId,
                                Collectors.mapping(
                                        provider ->
                                                new ProviderCatalogAlternativeResponse(
                                                        provider.getProviderId(),
                                                        provider.getProviderName(),
                                                        prices.get(
                                                                provider.getBookId()
                                                                        + ":"
                                                                        + provider.getProviderId()
                                                        )
                                                ),
                                        Collectors.toList()
                                )
                        )
                );
    }

    private ProviderCatalogBookResponse toResponse(
            ProviderBook providerBook,
            Map<Long, EditorialPrice> priceByBookId,
            Map<Long, Inventory> inventoryByBookId,
            Map<Long, PurchaseRequirement> requirementByBookId,
            Map<Long, List<ProviderCatalogAlternativeResponse>> alternativesByBookId
    ) {

        var book = providerBook.getBook();

        Long bookId = book.getId();

        EditorialPrice price = priceByBookId.get(bookId);

        Inventory inventory = inventoryByBookId.get(bookId);

        PurchaseRequirement requirement = requirementByBookId.get(bookId);

        return new ProviderCatalogBookResponse(
                providerBook.getId(),
                bookId,
                book.getPreferredIsbn(),
                book.getTitle(),
                book.getCoverUrl(),

                providerBook.getExternalCode(),

                price != null
                        ? price.getPrice()
                        : null,

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

    private void validateProvider(Long providerId) {

        providerRepository
                .findById(providerId)
                .filter(PriceListProvider::isActive)
                .orElseThrow(() ->
                        new BusinessException(
                                "El proveedor seleccionado no se encuentra activo."
                        )
                );
    }
}