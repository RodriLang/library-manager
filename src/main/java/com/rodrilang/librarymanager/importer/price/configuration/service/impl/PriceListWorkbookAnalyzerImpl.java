package com.rodrilang.librarymanager.importer.price.configuration.service.impl;

import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.importer.price.configuration.dto.analysis.PriceListPreviewRowResponse;
import com.rodrilang.librarymanager.importer.price.configuration.dto.analysis.PriceListSheetAnalysisResponse;
import com.rodrilang.librarymanager.importer.price.configuration.dto.analysis.PriceListSuggestedMappingResponse;
import com.rodrilang.librarymanager.importer.price.configuration.dto.analysis.PriceListWorkbookAnalysisResponse;
import com.rodrilang.librarymanager.importer.price.configuration.dto.analysis.internal.PriceListSheetPreview;
import com.rodrilang.librarymanager.importer.price.configuration.analysis.StreamingPriceListWorkbookReader;
import com.rodrilang.librarymanager.importer.price.configuration.service.PriceListMappingSuggester;
import com.rodrilang.librarymanager.importer.price.configuration.service.PriceListWorkbookAnalyzer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Semaphore;

@Service
@RequiredArgsConstructor
public class PriceListWorkbookAnalyzerImpl
        implements PriceListWorkbookAnalyzer {

    private static final Set<String> HEADER_KEYWORDS = Set.of(
            "isbn",
            "ean",
            "codigo",
            "barra",
            "titulo",
            "autor",
            "editorial",
            "sello",
            "pvp",
            "precio",
            "genero",
            "paginas",
            "idioma",
            "sinopsis",
            "descripcion",
            "stock"
    );

    private final StreamingPriceListWorkbookReader workbookReader;
    private final PriceListMappingSuggester mappingSuggester;

    private final Semaphore analysisSemaphore =
            new Semaphore(1, true);

    @Override
    public PriceListWorkbookAnalysisResponse analyze(
            MultipartFile file
    ) {
        if (!analysisSemaphore.tryAcquire()) {
            throw new BusinessException(
                    "Ya hay una plantilla siendo analizada. "
                            + "Esperá unos instantes e intentá nuevamente."
            );
        }

        try {
            List<PriceListSheetAnalysisResponse> sheets =
                    workbookReader.read(file)
                            .stream()
                            .map(this::analyzeSheet)
                            .toList();

            return new PriceListWorkbookAnalysisResponse(
                    file.getOriginalFilename(),
                    sheets
            );
        } finally {
            analysisSemaphore.release();
        }
    }

    private PriceListSheetAnalysisResponse analyzeSheet(
            PriceListSheetPreview sheet
    ) {
        Integer suggestedHeaderRowIndex =
                detectHeaderRow(sheet.rows());

        List<PriceListSuggestedMappingResponse>
                suggestedMappings =
                resolveSuggestedMappings(
                        sheet.rows(),
                        suggestedHeaderRowIndex
                );

        return new PriceListSheetAnalysisResponse(
                sheet.sheetIndex(),
                sheet.sheetName(),
                sheet.observedRowCount(),
                sheet.columnCount(),
                sheet.truncated(),
                suggestedHeaderRowIndex,
                suggestedMappings,
                sheet.rows()
        );
    }

    private List<PriceListSuggestedMappingResponse>
    resolveSuggestedMappings(
            List<PriceListPreviewRowResponse> preview,
            Integer headerRowIndex
    ) {
        if (headerRowIndex == null) {
            return List.of();
        }

        return preview.stream()
                .filter(row ->
                        row.rowIndex().equals(headerRowIndex)
                )
                .findFirst()
                .map(mappingSuggester::suggest)
                .orElseGet(List::of);
    }

    private Integer detectHeaderRow(
            List<PriceListPreviewRowResponse> rows
    ) {
        Integer bestRow = null;
        int bestScore = 0;

        for (PriceListPreviewRowResponse row : rows) {
            int score =
                    calculateHeaderScore(row.cells());

            if (score > bestScore) {
                bestScore = score;
                bestRow = row.rowIndex();
            }
        }

        return bestScore >= 2
                ? bestRow
                : null;
    }

    private int calculateHeaderScore(
            List<String> cells
    ) {
        int score = 0;

        for (String cell : cells) {
            String normalized = normalize(cell);

            if (normalized.isBlank()) {
                continue;
            }

            boolean matches = HEADER_KEYWORDS.stream()
                    .anyMatch(normalized::contains);

            if (matches) {
                score++;
            }
        }

        return score;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }

        String normalized = Normalizer.normalize(
                value,
                Normalizer.Form.NFD
        );

        return normalized
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }
}