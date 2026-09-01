package com.rodrilang.librarymanager.editorialprice.service.impl;

import com.rodrilang.librarymanager.editorialprice.dto.internal.EditorialPriceHealthSummaryCounts;
import com.rodrilang.librarymanager.editorialprice.dto.response.EditorialPriceHealthCountResponse;
import com.rodrilang.librarymanager.editorialprice.dto.response.EditorialPriceHealthIssueResponse;
import com.rodrilang.librarymanager.editorialprice.dto.response.EditorialPriceHealthSummaryResponse;
import com.rodrilang.librarymanager.editorialprice.enums.EditorialPriceConflictScope;
import com.rodrilang.librarymanager.editorialprice.enums.EditorialPriceHealthIssueType;
import com.rodrilang.librarymanager.editorialprice.repository.EditorialPriceHealthRepository;
import com.rodrilang.librarymanager.editorialprice.service.EditorialPriceHealthService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EditorialPriceHealthServiceImpl implements EditorialPriceHealthService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    private final EditorialPriceHealthRepository repository;

    @Value("${app.editorial-price.health.stale-days:90}")
    private int staleDays;

    @Override
    @Cacheable(cacheNames = "editorialPriceHealthSummary", sync = true)
    public EditorialPriceHealthSummaryResponse getSummary() {
        LocalDate today = today();
        EditorialPriceHealthSummaryCounts counts = repository.getSummary(today, today.minusDays(staleDays));

        return new EditorialPriceHealthSummaryResponse(
                counts.totalBooksWithIssues(),
                counts.nextPeriodSourceConflict(),
                List.of(
                        new EditorialPriceHealthCountResponse(EditorialPriceHealthIssueType.SOURCE_CONFLICT, counts.sourceConflict()),
                        new EditorialPriceHealthCountResponse(EditorialPriceHealthIssueType.NO_CURRENT_PRICE, counts.noCurrentPrice()),
                        new EditorialPriceHealthCountResponse(EditorialPriceHealthIssueType.FUTURE_ONLY, counts.futureOnly()),
                        new EditorialPriceHealthCountResponse(EditorialPriceHealthIssueType.STALE_EVIDENCE, counts.staleEvidence()),
                        new EditorialPriceHealthCountResponse(EditorialPriceHealthIssueType.EXTERNAL_REFERENCE_ONLY, counts.externalReferenceOnly())
                )
        );
    }

    @Override
    public Page<EditorialPriceHealthIssueResponse> findIssues(
            EditorialPriceHealthIssueType type,
            EditorialPriceConflictScope conflictScope,
            String query,
            Pageable pageable
    ) {
        LocalDate today = today();

        return repository.findIssues(
                type,
                conflictScope,
                normalizeQuery(query),
                today,
                today.minusDays(staleDays),
                pageable
        );
    }

    private LocalDate today() {
        return LocalDate.now(BUSINESS_ZONE);
    }

    private String normalizeQuery(String query) {
        if (query == null) return null;
        String normalized = query.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}