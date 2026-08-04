package com.rodrilang.librarymanager.importer.price.service.impl;

import com.rodrilang.librarymanager.importer.price.dto.PriceListIdentifier;
import com.rodrilang.librarymanager.importer.price.dto.PriceListRow;
import com.rodrilang.librarymanager.importer.price.resolver.PriceListIdentifierResolver;
import com.rodrilang.librarymanager.importer.price.service.PriceListRowDeduplicator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class PriceListRowDeduplicatorImpl implements PriceListRowDeduplicator {

    private final PriceListIdentifierResolver identifierResolver;

    @Override
    public List<PriceListRow> deduplicate(List<PriceListRow> rows) {
        Map<String, PriceListRow> rowsByKey = new LinkedHashMap<>();

        for (PriceListRow row : rows) {
            rowsByKey.merge(resolveDeduplicationKey(row), row, this::choosePreferredRow);
        }

        return new ArrayList<>(rowsByKey.values());
    }

    private String resolveDeduplicationKey(PriceListRow row) {
        PriceListIdentifier identifier = identifierResolver.resolve(row);

        return switch (identifier.type()) {
            case ISBN -> "ISBN:" + identifier.isbn13();
            case EXTERNAL_CODE -> "EXTERNAL:" + identifier.externalCode();
            case EMPTY -> "ROW:" + row.rowNumber();
        };
    }

    private PriceListRow choosePreferredRow(
            PriceListRow first,
            PriceListRow second
    ) {
        BigDecimal firstPrice = first.retailPrice();
        BigDecimal secondPrice = second.retailPrice();

        if (firstPrice == null && secondPrice == null) {
            return first;
        }

        if (firstPrice == null) {
            return second;
        }

        if (secondPrice == null) {
            return first;
        }

        if (firstPrice.compareTo(secondPrice) == 0) {
            return first;
        }

        log.warn(
                "Conflicting prices for same identifier. firstRow={} secondRow={} identifier={} firstPrice={} secondPrice={}",
                first.rowNumber(),
                second.rowNumber(),
                first.preferredIdentifier(),
                firstPrice,
                secondPrice
        );

        return first;
    }
}