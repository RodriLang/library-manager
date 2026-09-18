package com.rodrilang.librarymanager.profitability.service;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.profitability.dto.response.ProfitabilityReportResponse;
import com.rodrilang.librarymanager.profitability.dto.response.ProfitabilitySummaryResponse;
import com.rodrilang.librarymanager.profitability.repository.ProfitabilityReportRepository;
import com.rodrilang.librarymanager.profitability.repository.projection.ProfitabilityReportAggregateProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ProfitabilityReportService {

    private final ProfitabilityReportRepository repository;
    private final ProfitabilityCalculator calculator;
    private final BookstoreContext bookstoreContext;

    @Transactional(readOnly = true)
    public ProfitabilityReportResponse get(Instant from, Instant to) {
        validateRange(from, to);

        ProfitabilityReportAggregateProjection aggregate = repository.summarize(
                bookstoreContext.getCurrentBookstoreId(),
                from,
                to
        );

        int totalUnits = Math.toIntExact(value(aggregate.getTotalUnits()));
        int realCostUnits = Math.toIntExact(value(aggregate.getRealCostUnits()));
        int estimatedCostUnits = Math.toIntExact(value(aggregate.getEstimatedCostUnits()));

        ProfitabilitySummaryResponse summary = calculator.calculate(
                aggregate.getNetSalesAmount(),
                totalUnits,
                aggregate.getRealCostAmount(),
                aggregate.getEstimatedCostAmount(),
                realCostUnits,
                estimatedCostUnits
        );

        return new ProfitabilityReportResponse(
                from,
                to,
                value(aggregate.getSaleCount()),
                summary
        );
    }

    private void validateRange(Instant from, Instant to) {
        if (from == null || to == null) {
            throw new BusinessException("Deben informarse las fechas desde y hasta.");
        }
        if (from.isAfter(to)) {
            throw new BusinessException("La fecha desde no puede ser posterior a la fecha hasta.");
        }
    }

    private long value(Long value) {
        return value != null ? value : 0L;
    }
}
