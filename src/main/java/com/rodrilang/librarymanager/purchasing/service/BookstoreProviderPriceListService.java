package com.rodrilang.librarymanager.purchasing.service;

import com.rodrilang.librarymanager.importer.price.dto.internal.PriceListMetadata;
import com.rodrilang.librarymanager.importer.price.dto.internal.PriceListRow;
import com.rodrilang.librarymanager.inventory.pricing.model.InventoryPriceImport;
import com.rodrilang.librarymanager.inventory.pricing.model.InventoryPriceImportProviderRow;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.model.Bookstore;
import com.rodrilang.librarymanager.provider.bookstore.model.BookstoreProvider;
import com.rodrilang.librarymanager.provider.bookstore.repository.BookstoreProviderRepository;
import com.rodrilang.librarymanager.provider.catalog.service.BookstoreCatalogObservationService;
import com.rodrilang.librarymanager.provider.model.Provider;
import com.rodrilang.librarymanager.purchasing.model.BookstoreProviderBookTerm;
import com.rodrilang.librarymanager.purchasing.repository.BookstoreProviderBookTermRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookstoreProviderPriceListService {

    private final BookstoreProviderRepository bookstoreProviderRepository;
    private final BookstoreProviderBookTermRepository termRepository;
    private final BookstoreCatalogObservationService catalogObservationService;

    /**
     * Aplica la dimensión "proveedor" de una lista ya confirmada por la
     * librería. Sólo conserva el último precio conocido por libro; nunca crea
     * una serie histórica paralela a InventoryPrice.
     */
    @Transactional
    public void apply(
            InventoryPriceImport priceImport,
            Provider provider,
            List<InventoryPriceImportProviderRow> stagedRows
    ) {
        if (provider == null || stagedRows == null || stagedRows.isEmpty()) {
            return;
        }

        Bookstore bookstore = priceImport.getBookstore();
        ensureBookstoreProvider(bookstore, provider);

        List<PriceListRow> rows = stagedRows.stream()
                .map(this::toPriceListRow)
                .toList();

        Map<Integer, Book> booksByRow = catalogObservationService
                .observeAndResolve(provider.getId(), rows);

        Map<Long, List<ResolvedRow>> rowsByBookId = new LinkedHashMap<>();
        for (InventoryPriceImportProviderRow staged : stagedRows) {
            Book book = staged.getBook() != null
                    ? staged.getBook()
                    : booksByRow.get(staged.getRowNumber());
            if (book == null) {
                continue;
            }
            rowsByBookId
                    .computeIfAbsent(book.getId(), ignored -> new ArrayList<>())
                    .add(new ResolvedRow(book, staged));
        }

        LocalDate effectiveFrom = priceImport.getEffectiveFrom();

        for (List<ResolvedRow> group : rowsByBookId.values()) {
            ResolvedRow first = group.getFirst();
            Book book = first.book();

            BookstoreProviderBookTerm term = termRepository
                    .findByBookstoreIdAndProviderIdAndBookId(
                            bookstore.getId(),
                            provider.getId(),
                            book.getId()
                    )
                    .orElseGet(() -> BookstoreProviderBookTerm.builder()
                            .bookstore(bookstore)
                            .provider(provider)
                            .book(book)
                            .build());

            LocalDate previousSeen = term.getLastSeenInPriceListAt();
            if (previousSeen == null || !effectiveFrom.isBefore(previousSeen)) {
                term.setLastSeenInPriceListAt(effectiveFrom);
            }

            Set<BigDecimal> prices = new LinkedHashSet<>();
            for (ResolvedRow resolved : group) {
                BigDecimal amount = normalizePositive(resolved.row().getIncomingPrice());
                if (amount != null) {
                    prices.add(amount);
                }
            }

            boolean listIsNotOlder = term.getLatestListEffectiveFrom() == null
                    || !effectiveFrom.isBefore(term.getLatestListEffectiveFrom());

            if (prices.size() == 1 && listIsNotOlder) {
                term.setLatestListPrice(prices.iterator().next());
                term.setLatestListEffectiveFrom(effectiveFrom);
                term.setLastPriceImport(priceImport);
            } else if (prices.size() > 1) {
                log.warn(
                        "La lista {} contiene precios distintos para el mismo libro {} y proveedor {}. "
                                + "Se actualiza la fecha de aparición pero se conserva el último precio inequívoco.",
                        priceImport.getId(),
                        book.getId(),
                        provider.getId()
                );
            }

            termRepository.save(term);
        }
    }

    private void ensureBookstoreProvider(Bookstore bookstore, Provider provider) {
        BookstoreProvider relation = bookstoreProviderRepository
                .findByBookstoreIdAndProviderId(bookstore.getId(), provider.getId())
                .orElseGet(() -> BookstoreProvider.builder()
                        .bookstore(bookstore)
                        .provider(provider)
                        .active(true)
                        .build());
        relation.setActive(true);
        bookstoreProviderRepository.save(relation);
    }

    private PriceListRow toPriceListRow(InventoryPriceImportProviderRow row) {
        PriceListMetadata metadata = PriceListMetadata.builder()
                .externalCode(row.getExternalCode())
                .build();
        return new PriceListRow(
                row.getRowNumber(),
                row.getIsbn(),
                row.getTitle(),
                row.getAuthor(),
                row.getPublisher(),
                row.getIncomingPrice(),
                null,
                null,
                metadata
        );
    }

    private BigDecimal normalizePositive(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            return null;
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private record ResolvedRow(Book book, InventoryPriceImportProviderRow row) {
    }
}
