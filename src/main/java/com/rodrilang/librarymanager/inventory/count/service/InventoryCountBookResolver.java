package com.rodrilang.librarymanager.inventory.count.service;

import com.rodrilang.librarymanager.catalog.candidate.model.CatalogCandidate;
import com.rodrilang.librarymanager.catalog.candidate.model.CatalogCandidateStatus;
import com.rodrilang.librarymanager.catalog.candidate.service.CatalogCandidateRegistry;
import com.rodrilang.librarymanager.enums.BookCondition;
import com.rodrilang.librarymanager.inventory.count.dto.internal.InventoryCountResolution;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountItemStatus;
import com.rodrilang.librarymanager.isbn.model.ParsedIsbn;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.model.Bookstore;
import com.rodrilang.librarymanager.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InventoryCountBookResolver {

    private final BookRepository bookRepository;
    private final CatalogCandidateRegistry candidateRegistry;
    private final InventoryCountPriceResolver priceResolver;

    public InventoryCountResolution resolve(ParsedIsbn isbn, Bookstore bookstore, BookCondition condition) {
        Book book = findLocalBook(isbn);
        if (book != null) {
            return resolvedBook(book, bookstore.getId(), condition);
        }

        CatalogCandidate candidate = candidateRegistry.getOrCreate(isbn, bookstore);
        if (candidate.getStatus() == CatalogCandidateStatus.RESOLVED && candidate.getResolvedBook() != null) {
            return resolvedBook(candidate.getResolvedBook(), bookstore.getId(), condition);
        }

        return new InventoryCountResolution(null, candidate, InventoryCountItemStatus.PENDING_CATALOG);
    }

    private InventoryCountResolution resolvedBook(Book book, Long bookstoreId, BookCondition condition) {
        InventoryCountItemStatus status = priceResolver.requiresPrice(book, bookstoreId, condition)
                ? InventoryCountItemStatus.PENDING_PRICE
                : InventoryCountItemStatus.RESOLVED;

        return new InventoryCountResolution(book, null, status);
    }

    private Book findLocalBook(ParsedIsbn isbn) {
        Book book = bookRepository.findByIsbn13(isbn.isbn13()).orElse(null);
        if (book != null || isbn.isbn10() == null) {
            return book;
        }

        return bookRepository.findByIsbn10(isbn.isbn10()).orElse(null);
    }
}
