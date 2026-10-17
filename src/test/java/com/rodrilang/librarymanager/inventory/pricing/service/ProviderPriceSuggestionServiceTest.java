package com.rodrilang.librarymanager.inventory.pricing.service;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.inventory.pricing.dto.ProviderPriceFreshness;
import com.rodrilang.librarymanager.inventory.pricing.dto.ProviderPriceSuggestionStatus;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.provider.model.Provider;
import com.rodrilang.librarymanager.purchasing.model.BookstoreProviderBookTerm;
import com.rodrilang.librarymanager.purchasing.repository.BookstoreProviderBookTermRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class ProviderPriceSuggestionServiceTest {

    private final ProviderPriceSuggestionService service = new ProviderPriceSuggestionService(
            mock(BookstoreContext.class),
            mock(BookstoreProviderBookTermRepository.class)
    );

    @Test
    void suggestsPriceWhenLatestMonthIsConsistentAcrossProviders() {
        var result = service.buildSuggestion(
                10L,
                List.of(
                        term(10L, 1L, "Waldhuter", "12000", LocalDate.of(2026, 10, 1)),
                        term(10L, 2L, "SBS", "12000.00", LocalDate.of(2026, 10, 8)),
                        term(10L, 3L, "Luongo", "11000", LocalDate.of(2026, 9, 1))
                ),
                YearMonth.of(2026, 10)
        );

        assertEquals(ProviderPriceSuggestionStatus.CONSISTENT, result.status());
        assertEquals(ProviderPriceFreshness.CURRENT, result.freshness());
        assertTrue(result.autoApplicable());
        assertEquals(0, new BigDecimal("12000").compareTo(result.suggestedPrice()));
        assertEquals(YearMonth.of(2026, 10), result.latestPeriod());
        assertEquals(3, result.prices().size());
        assertTrue(result.prices().get(0).latestPeriod());
        assertTrue(result.prices().get(1).latestPeriod());
        assertFalse(result.prices().get(2).latestPeriod());
    }

    @Test
    void doesNotSuggestWhenProvidersDisagreeInLatestMonth() {
        var result = service.buildSuggestion(
                10L,
                List.of(
                        term(10L, 1L, "Waldhuter", "12000", LocalDate.of(2026, 10, 1)),
                        term(10L, 2L, "SBS", "13000", LocalDate.of(2026, 10, 8)),
                        term(10L, 3L, "Luongo", "11000", LocalDate.of(2026, 9, 1))
                ),
                YearMonth.of(2026, 10)
        );

        assertEquals(ProviderPriceSuggestionStatus.CONFLICT, result.status());
        assertEquals(ProviderPriceFreshness.CURRENT, result.freshness());
        assertFalse(result.autoApplicable());
        assertNull(result.suggestedPrice());
    }

    @Test
    void marksSuggestionAsStaleWhenLatestPriceIsFromPreviousMonth() {
        var result = service.buildSuggestion(
                10L,
                List.of(
                        term(10L, 1L, "SBS", "12000", LocalDate.of(2026, 9, 20)),
                        term(10L, 2L, "Waldhuter", "12000", LocalDate.of(2026, 9, 1))
                ),
                YearMonth.of(2026, 10)
        );

        assertEquals(ProviderPriceSuggestionStatus.CONSISTENT, result.status());
        assertEquals(ProviderPriceFreshness.STALE, result.freshness());
        assertTrue(result.autoApplicable());
        assertEquals(0, new BigDecimal("12000").compareTo(result.suggestedPrice()));
    }

    @Test
    void identifiesSingleSource() {
        var result = service.buildSuggestion(
                10L,
                List.of(term(10L, 1L, "SBS", "12000", LocalDate.of(2026, 10, 5))),
                YearMonth.of(2026, 10)
        );

        assertEquals(ProviderPriceSuggestionStatus.SINGLE_SOURCE, result.status());
        assertEquals(ProviderPriceFreshness.CURRENT, result.freshness());
        assertTrue(result.autoApplicable());
    }

    @Test
    void returnsNoDataWithoutKnownProviderPrices() {
        var result = service.buildSuggestion(10L, List.of(), YearMonth.of(2026, 10));

        assertEquals(ProviderPriceSuggestionStatus.NO_DATA, result.status());
        assertNull(result.freshness());
        assertNull(result.latestPeriod());
        assertNull(result.suggestedPrice());
        assertFalse(result.autoApplicable());
        assertTrue(result.prices().isEmpty());
    }

    private BookstoreProviderBookTerm term(
            Long bookId,
            Long providerId,
            String providerName,
            String price,
            LocalDate effectiveFrom
    ) {
        return BookstoreProviderBookTerm.builder()
                .book(Book.builder().id(bookId).title("Libro").build())
                .provider(Provider.builder().id(providerId).name(providerName).active(true).build())
                .latestListPrice(new BigDecimal(price))
                .latestListEffectiveFrom(effectiveFrom)
                .build();
    }
}
