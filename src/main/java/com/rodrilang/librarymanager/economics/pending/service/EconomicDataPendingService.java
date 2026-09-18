package com.rodrilang.librarymanager.economics.pending.service;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.economics.pending.dto.response.EconomicDataPendingItemResponse;
import com.rodrilang.librarymanager.economics.pending.dto.response.EconomicDataPendingSummaryResponse;
import com.rodrilang.librarymanager.economics.pending.model.EconomicDataPendingReason;
import com.rodrilang.librarymanager.economics.pending.repository.EconomicDataPendingRepository;
import com.rodrilang.librarymanager.economics.pending.repository.EconomicDataPendingRow;
import com.rodrilang.librarymanager.economics.pending.repository.EconomicDataPendingSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EconomicDataPendingService {

    private static final ZoneId ANAQUEL_ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    private final EconomicDataPendingRepository repository;
    private final BookstoreContext bookstoreContext;

    @Transactional(readOnly = true)
    public EconomicDataPendingSummaryResponse summary() {
        EconomicDataPendingSummary summary = repository.summarize(
                bookstoreContext.getCurrentBookstoreId(),
                LocalDate.now(ANAQUEL_ZONE)
        );

        return new EconomicDataPendingSummaryResponse(
                summary.totalStockUnits(),
                summary.unknownCostUnits(),
                summary.estimatedCostUnits(),
                summary.missingDiscountUnits(),
                summary.missingCurrentPriceUnits(),
                summary.pendingBookCount(),
                summary.booksWithoutCommercialTerms()
        );
    }

    @Transactional(readOnly = true)
    public Page<EconomicDataPendingItemResponse> findPending(
            EconomicDataPendingReason reason,
            String search,
            Pageable pageable
    ) {
        return repository.findPending(
                bookstoreContext.getCurrentBookstoreId(),
                LocalDate.now(ANAQUEL_ZONE),
                reason,
                search,
                pageable
        ).map(this::toResponse);
    }

    private EconomicDataPendingItemResponse toResponse(EconomicDataPendingRow row) {
        List<EconomicDataPendingReason> reasons = new ArrayList<>();
        if (row.unknownCostUnits() > 0) {
            reasons.add(EconomicDataPendingReason.UNKNOWN_COST);
        }
        if (row.missingDiscountUnits() > 0) {
            reasons.add(EconomicDataPendingReason.MISSING_DISCOUNT);
        }
        if (row.currentEditorialPrice() == null) {
            reasons.add(EconomicDataPendingReason.MISSING_CURRENT_PRICE);
        }
        if (!row.hasCommercialTerm()) {
            reasons.add(EconomicDataPendingReason.NO_COMMERCIAL_TERM);
        }

        return new EconomicDataPendingItemResponse(
                row.bookId(),
                row.isbn(),
                row.title(),
                row.stockUnits(),
                row.unknownCostUnits(),
                row.estimatedCostUnits(),
                row.missingDiscountUnits(),
                row.currentEditorialPrice(),
                row.hasCommercialTerm(),
                List.copyOf(reasons)
        );
    }
}
