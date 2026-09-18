package com.rodrilang.librarymanager.purchasing.service;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.provider.model.Provider;
import com.rodrilang.librarymanager.inventory.cost.service.InventoryCostCalculator;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.model.Bookstore;
import com.rodrilang.librarymanager.purchasing.dto.request.UpsertProviderBookTermRequest;
import com.rodrilang.librarymanager.purchasing.dto.response.ProviderBookTermResponse;
import com.rodrilang.librarymanager.purchasing.model.BookstoreProviderBookTerm;
import com.rodrilang.librarymanager.purchasing.repository.BookstoreProviderBookTermRepository;
import com.rodrilang.librarymanager.repository.BookRepository;
import com.rodrilang.librarymanager.repository.BookstoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProviderBookTermService {

    private final BookstoreProviderBookTermRepository repository;
    private final BookRepository bookRepository;
    private final BookstoreRepository bookstoreRepository;
    private final InventoryCostCalculator calculator;
    private final ProviderResolver providerResolver;
    private final BookstoreContext bookstoreContext;
    private final PurchasingMapper mapper;

    @Transactional(readOnly = true)
    public List<ProviderBookTermResponse> findByProvider(Long providerId) {
        providerResolver.requirePurchasable(providerId);
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();

        return repository
                .findAllByBookstoreIdAndProviderIdOrderByBookTitleAsc(bookstoreId, providerId)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional
    public ProviderBookTermResponse upsert(Long providerId, UpsertProviderBookTermRequest request) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        Provider provider = providerResolver.requirePurchasable(providerId);
        Book book = bookRepository.findById(request.bookId())
                .orElseThrow(() -> new BusinessException("Libro no encontrado"));

        BookstoreProviderBookTerm term = repository
                .findByBookstoreIdAndProviderIdAndBookId(bookstoreId, providerId, book.getId())
                .orElseGet(() -> BookstoreProviderBookTerm.builder()
                        .bookstore(requireBookstore(bookstoreId))
                        .provider(provider)
                        .book(book)
                        .build());

        term.setDiscountPercentage(calculator.percentage(request.discountPercentage()));
        return mapper.toResponse(repository.save(term));
    }

    @Transactional
    public void rememberPurchase(
            Bookstore bookstore,
            Provider provider,
            Book book,
            BigDecimal discount,
            LocalDate purchaseDate
    ) {
        if (discount == null) return;

        BookstoreProviderBookTerm term = repository
                .findByBookstoreIdAndProviderIdAndBookId(bookstore.getId(), provider.getId(), book.getId())
                .orElseGet(() -> BookstoreProviderBookTerm.builder()
                        .bookstore(bookstore)
                        .provider(provider)
                        .book(book)
                        .build());

        term.setDiscountPercentage(calculator.percentage(discount));
        term.setLastPurchaseDate(purchaseDate);
        repository.save(term);
    }

    private Bookstore requireBookstore(Long bookstoreId) {
        return bookstoreRepository.findById(bookstoreId)
                .orElseThrow(() -> new BusinessException("Librería no encontrada"));
    }
}
