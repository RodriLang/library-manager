package com.rodrilang.librarymanager.purchasing.service;

import com.rodrilang.librarymanager.inventory.pricing.model.InventoryPriceImport;
import com.rodrilang.librarymanager.inventory.pricing.model.InventoryPriceImportProviderRow;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.model.Bookstore;
import com.rodrilang.librarymanager.provider.bookstore.repository.BookstoreProviderRepository;
import com.rodrilang.librarymanager.provider.catalog.service.BookstoreCatalogObservationService;
import com.rodrilang.librarymanager.provider.model.Provider;
import com.rodrilang.librarymanager.purchasing.model.BookstoreProviderBookTerm;
import com.rodrilang.librarymanager.purchasing.repository.BookstoreProviderBookTermRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookstoreProviderPriceListServiceTest {

    @Test
    void replacesOnlyTheLatestProviderSnapshotWithoutCreatingPriceHistory() {
        BookstoreProviderRepository bookstoreProviders = mock(BookstoreProviderRepository.class);
        BookstoreProviderBookTermRepository terms = mock(BookstoreProviderBookTermRepository.class);
        BookstoreCatalogObservationService observations = mock(BookstoreCatalogObservationService.class);
        BookstoreProviderPriceListService service = new BookstoreProviderPriceListService(
                bookstoreProviders,
                terms,
                observations
        );

        Bookstore bookstore = Bookstore.builder().id(1L).build();
        Provider provider = Provider.builder().id(2L).name("Proveedor").active(true).build();
        Book book = Book.builder().id(3L).isbn13("9789875669284").title("Libro").build();

        InventoryPriceImport priceImport = InventoryPriceImport.builder()
                .id(10L)
                .bookstore(bookstore)
                .provider(provider)
                .effectiveFrom(LocalDate.of(2026, 10, 1))
                .build();

        InventoryPriceImportProviderRow row = InventoryPriceImportProviderRow.builder()
                .rowNumber(2)
                .isbn("9789875669284")
                .title("Libro")
                .incomingPrice(new BigDecimal("49900"))
                .build();

        BookstoreProviderBookTerm existing = BookstoreProviderBookTerm.builder()
                .id(20L)
                .bookstore(bookstore)
                .provider(provider)
                .book(book)
                .latestListPrice(new BigDecimal("48000"))
                .latestListEffectiveFrom(LocalDate.of(2026, 9, 1))
                .lastSeenInPriceListAt(LocalDate.of(2026, 9, 1))
                .build();

        when(bookstoreProviders.findByBookstoreIdAndProviderId(1L, 2L)).thenReturn(Optional.empty());
        when(terms.findByBookstoreIdAndProviderIdAndBookId(1L, 2L, 3L)).thenReturn(Optional.of(existing));
        when(observations.observeAndResolve(eq(2L), any())).thenReturn(Map.of(2, book));

        service.apply(priceImport, provider, List.of(row));

        assertEquals(0, new BigDecimal("49900").compareTo(existing.getLatestListPrice()));
        assertEquals(LocalDate.of(2026, 10, 1), existing.getLatestListEffectiveFrom());
        assertEquals(LocalDate.of(2026, 10, 1), existing.getLastSeenInPriceListAt());
        assertEquals(priceImport, existing.getLastPriceImport());
        verify(terms).save(existing);
    }

    @Test
    void anOlderListDoesNotOverwriteANewerSnapshot() {
        BookstoreProviderRepository bookstoreProviders = mock(BookstoreProviderRepository.class);
        BookstoreProviderBookTermRepository terms = mock(BookstoreProviderBookTermRepository.class);
        BookstoreCatalogObservationService observations = mock(BookstoreCatalogObservationService.class);
        BookstoreProviderPriceListService service = new BookstoreProviderPriceListService(
                bookstoreProviders,
                terms,
                observations
        );

        Bookstore bookstore = Bookstore.builder().id(1L).build();
        Provider provider = Provider.builder().id(2L).name("Proveedor").active(true).build();
        Book book = Book.builder().id(3L).isbn13("9789875669284").title("Libro").build();

        InventoryPriceImport oldImport = InventoryPriceImport.builder()
                .id(11L)
                .bookstore(bookstore)
                .provider(provider)
                .effectiveFrom(LocalDate.of(2026, 9, 1))
                .build();

        InventoryPriceImportProviderRow row = InventoryPriceImportProviderRow.builder()
                .rowNumber(2)
                .isbn("9789875669284")
                .title("Libro")
                .incomingPrice(new BigDecimal("45000"))
                .build();

        BookstoreProviderBookTerm existing = BookstoreProviderBookTerm.builder()
                .id(20L)
                .bookstore(bookstore)
                .provider(provider)
                .book(book)
                .latestListPrice(new BigDecimal("49900"))
                .latestListEffectiveFrom(LocalDate.of(2026, 10, 1))
                .lastSeenInPriceListAt(LocalDate.of(2026, 10, 1))
                .build();

        when(bookstoreProviders.findByBookstoreIdAndProviderId(1L, 2L)).thenReturn(Optional.empty());
        when(terms.findByBookstoreIdAndProviderIdAndBookId(1L, 2L, 3L)).thenReturn(Optional.of(existing));
        when(observations.observeAndResolve(eq(2L), any())).thenReturn(Map.of(2, book));

        service.apply(oldImport, provider, List.of(row));

        assertEquals(0, new BigDecimal("49900").compareTo(existing.getLatestListPrice()));
        assertEquals(LocalDate.of(2026, 10, 1), existing.getLatestListEffectiveFrom());
        assertEquals(LocalDate.of(2026, 10, 1), existing.getLastSeenInPriceListAt());
    }
}
