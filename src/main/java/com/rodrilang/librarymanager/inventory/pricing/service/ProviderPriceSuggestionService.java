package com.rodrilang.librarymanager.inventory.pricing.service;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.inventory.pricing.dto.ProviderPriceFreshness;
import com.rodrilang.librarymanager.inventory.pricing.dto.ProviderPriceSuggestionResponse;
import com.rodrilang.librarymanager.inventory.pricing.dto.ProviderPriceSuggestionStatus;
import com.rodrilang.librarymanager.inventory.pricing.dto.ProviderReportedPriceResponse;
import com.rodrilang.librarymanager.purchasing.model.BookstoreProviderBookTerm;
import com.rodrilang.librarymanager.purchasing.repository.BookstoreProviderBookTermRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProviderPriceSuggestionService {

    private final BookstoreContext bookstoreContext;
    private final BookstoreProviderBookTermRepository termRepository;

    @Transactional(readOnly = true)
    public ProviderPriceSuggestionResponse findForBook(Long bookId) {
        return findForBooks(List.of(bookId)).getFirst();
    }

    @Transactional(readOnly = true)
    public List<ProviderPriceSuggestionResponse> findForBooks(Collection<Long> bookIds) {
        List<Long> normalizedBookIds = normalizeBookIds(bookIds);
        if (normalizedBookIds.isEmpty()) {
            return List.of();
        }

        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        List<BookstoreProviderBookTerm> terms = termRepository.findPriceReferencesForBooks(
                bookstoreId,
                normalizedBookIds
        );

        Map<Long, List<BookstoreProviderBookTerm>> termsByBook = new LinkedHashMap<>();
        for (BookstoreProviderBookTerm term : terms) {
            termsByBook
                    .computeIfAbsent(term.getBook().getId(), ignored -> new ArrayList<>())
                    .add(term);
        }

        YearMonth currentMonth = YearMonth.now();
        return normalizedBookIds.stream()
                .map(bookId -> buildSuggestion(
                        bookId,
                        termsByBook.getOrDefault(bookId, List.of()),
                        currentMonth
                ))
                .toList();
    }

    ProviderPriceSuggestionResponse buildSuggestion(
            Long bookId,
            List<BookstoreProviderBookTerm> terms,
            YearMonth currentMonth
    ) {
        if (terms == null || terms.isEmpty()) {
            return new ProviderPriceSuggestionResponse(
                    bookId,
                    null,
                    false,
                    ProviderPriceSuggestionStatus.NO_DATA,
                    null,
                    null,
                    List.of()
            );
        }

        YearMonth latestPeriod = terms.stream()
                .map(BookstoreProviderBookTerm::getLatestListEffectiveFrom)
                .map(YearMonth::from)
                .max(YearMonth::compareTo)
                .orElseThrow();

        List<BookstoreProviderBookTerm> latestTerms = terms.stream()
                .filter(term -> YearMonth.from(term.getLatestListEffectiveFrom()).equals(latestPeriod))
                .toList();

        Set<BigDecimal> latestPrices = new LinkedHashSet<>();
        for (BookstoreProviderBookTerm term : latestTerms) {
            latestPrices.add(term.getLatestListPrice().stripTrailingZeros());
        }

        ProviderPriceSuggestionStatus status;
        BigDecimal suggestedPrice = null;
        boolean autoApplicable = false;

        if (latestPrices.size() > 1) {
            status = ProviderPriceSuggestionStatus.CONFLICT;
        } else {
            suggestedPrice = latestTerms.getFirst().getLatestListPrice();
            autoApplicable = true;
            status = latestTerms.size() == 1
                    ? ProviderPriceSuggestionStatus.SINGLE_SOURCE
                    : ProviderPriceSuggestionStatus.CONSISTENT;
        }

        ProviderPriceFreshness freshness = latestPeriod.isBefore(currentMonth)
                ? ProviderPriceFreshness.STALE
                : ProviderPriceFreshness.CURRENT;

        List<ProviderReportedPriceResponse> prices = terms.stream()
                .map(term -> new ProviderReportedPriceResponse(
                        term.getProvider().getId(),
                        term.getProvider().getName(),
                        term.getLatestListPrice(),
                        term.getLatestListEffectiveFrom(),
                        YearMonth.from(term.getLatestListEffectiveFrom()).equals(latestPeriod)
                ))
                .toList();

        return new ProviderPriceSuggestionResponse(
                bookId,
                suggestedPrice,
                autoApplicable,
                status,
                freshness,
                latestPeriod,
                prices
        );
    }

    private List<Long> normalizeBookIds(Collection<Long> bookIds) {
        if (bookIds == null || bookIds.isEmpty()) {
            return List.of();
        }
        return bookIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .limit(200)
                .toList();
    }
}
