package com.rodrilang.librarymanager.importer.price.service.impl;

import com.rodrilang.librarymanager.importer.price.dto.PriceListRow;
import com.rodrilang.librarymanager.importer.price.service.PriceListRowDeduplicator;
import com.rodrilang.librarymanager.isbn.model.ParsedIsbn;
import com.rodrilang.librarymanager.isbn.service.IsbnService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class PriceListRowDeduplicatorImpl implements PriceListRowDeduplicator {

    private final IsbnService isbnService;

    @Override
    public List<PriceListRow> deduplicate(List<PriceListRow> rows) {
        Map<String, PriceListRow> rowsByKey = new LinkedHashMap<>();

        for (PriceListRow row : rows) {
            String key = resolveDeduplicationKey(row);
            rowsByKey.merge(key, row, this::choosePreferredRow);
        }

        return new ArrayList<>(rowsByKey.values());
    }

    private String resolveDeduplicationKey(PriceListRow row) {
        String normalized = isbnService.normalize(row.isbn());
        ParsedIsbn parsedIsbn = isbnService.parse(normalized);

        if (parsedIsbn.valid()) {
            return "CANONICAL:" + parsedIsbn.isbn13();
        }

        if (isbnService.hasIsbn13Format(normalized)) {
            return "ISBN13_RAW:" + normalized;
        }

        if (isbnService.hasIsbn10Format(normalized)) {
            return "ISBN10_RAW:" + normalized;
        }

        return "ROW:" + row.rowNumber();
    }

    private PriceListRow choosePreferredRow(PriceListRow current, PriceListRow candidate) {
        boolean currentIsIsbn13 = isOriginalIsbn13(current.isbn());
        boolean candidateIsIsbn13 = isOriginalIsbn13(candidate.isbn());

        if (!currentIsIsbn13 && candidateIsIsbn13) {
            return candidate;
        }

        return current;
    }

    private boolean isOriginalIsbn13(String value) {
        String normalized = isbnService.normalize(value);
        return isbnService.hasIsbn13Format(normalized);
    }
}