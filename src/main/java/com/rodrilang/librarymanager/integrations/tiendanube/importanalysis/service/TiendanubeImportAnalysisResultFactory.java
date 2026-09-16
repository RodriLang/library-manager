package com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.service;

import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeProductResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeVariantResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.enums.TiendanubeImportAnalysisCandidateSource;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.enums.TiendanubeImportAnalysisItemStatus;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.enums.TiendanubeImportAnalysisMatchType;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.model.TiendanubeImportAnalysisCandidateData;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.model.TiendanubeImportAnalysisItemData;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.model.TiendanubeImportAnalysisMatchContext;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.model.TiendanubeImportAnalysisParsedIdentifier;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.model.TiendanubeImportAnalysisScoredBook;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.model.TiendanubeImportAnalysisScoredInventory;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.model.Inventory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Component
public class TiendanubeImportAnalysisResultFactory {

    public TiendanubeImportAnalysisItemData create(
            TiendanubeProductResponse product,
            TiendanubeVariantResponse variant,
            TiendanubeImportAnalysisParsedIdentifier identifier,
            TiendanubeImportAnalysisItemStatus status,
            TiendanubeImportAnalysisMatchType matchType,
            Long suggestedInventoryId,
            Long suggestedBookId,
            String message,
            List<TiendanubeImportAnalysisCandidateData> candidates
    ) {
        return new TiendanubeImportAnalysisItemData(
                product.id(),
                variant.id(),
                productName(product),
                variant.sku(),
                variant.barcode(),
                identifier == null ? null : identifier.parsed().isbn13(),
                identifier == null ? null : identifier.source(),
                identifier != null && identifier.parsed().recovered(),
                variant.price(),
                variant.stock(),
                mainImageUrl(product),
                product.published(),
                status,
                matchType,
                suggestedInventoryId,
                suggestedBookId,
                message,
                candidates
        );
    }

    public List<TiendanubeImportAnalysisCandidateData> inventoryCandidates(
            List<TiendanubeImportAnalysisScoredInventory> scored,
            TiendanubeImportAnalysisMatchContext context
    ) {
        List<TiendanubeImportAnalysisCandidateData> result = new ArrayList<>();

        for (int index = 0; index < scored.size(); index++) {
            TiendanubeImportAnalysisScoredInventory candidate = scored.get(index);
            result.add(inventoryCandidate(
                    candidate.inventory(),
                    index + 1,
                    candidate.score(),
                    candidate.matchType(),
                    !context.linksByInventoryId().containsKey(candidate.inventory().getId())
            ));
        }

        return result;
    }

    public List<TiendanubeImportAnalysisCandidateData> exactInventoryCandidates(
            List<Inventory> inventories,
            TiendanubeImportAnalysisMatchType matchType,
            TiendanubeImportAnalysisMatchContext context
    ) {
        List<TiendanubeImportAnalysisCandidateData> result = new ArrayList<>();

        for (int index = 0; index < inventories.size(); index++) {
            Inventory inventory = inventories.get(index);
            result.add(inventoryCandidate(
                    inventory,
                    index + 1,
                    1,
                    matchType,
                    !context.linksByInventoryId().containsKey(inventory.getId())
            ));
        }

        return result;
    }

    public TiendanubeImportAnalysisCandidateData inventoryCandidate(
            Inventory inventory,
            int rank,
            double score,
            TiendanubeImportAnalysisMatchType matchType,
            boolean available
    ) {
        return new TiendanubeImportAnalysisCandidateData(
                inventory.getId(),
                inventory.getBook().getId(),
                rank,
                TiendanubeImportAnalysisCandidateSource.INVENTORY,
                matchType,
                score,
                available
        );
    }

    public List<TiendanubeImportAnalysisCandidateData> catalogCandidates(
            Collection<Book> books,
            TiendanubeImportAnalysisMatchType matchType,
            double score
    ) {
        List<TiendanubeImportAnalysisCandidateData> result = new ArrayList<>();
        int rank = 1;

        for (Book book : books) {
            result.add(new TiendanubeImportAnalysisCandidateData(
                    null,
                    book.getId(),
                    rank++,
                    TiendanubeImportAnalysisCandidateSource.CATALOG,
                    matchType,
                    score,
                    false
            ));
        }

        return result;
    }

    public List<TiendanubeImportAnalysisCandidateData> catalogCandidates(
            List<TiendanubeImportAnalysisScoredBook> scored
    ) {
        List<TiendanubeImportAnalysisCandidateData> result = new ArrayList<>();

        for (int index = 0; index < scored.size(); index++) {
            TiendanubeImportAnalysisScoredBook candidate = scored.get(index);
            result.add(new TiendanubeImportAnalysisCandidateData(
                    null,
                    candidate.book().getId(),
                    index + 1,
                    TiendanubeImportAnalysisCandidateSource.CATALOG,
                    TiendanubeImportAnalysisMatchType.CATALOG_MATCH,
                    candidate.score(),
                    false
            ));
        }

        return result;
    }

    public String productName(TiendanubeProductResponse product) {
        if (product.name() == null || product.name().isEmpty()) {
            return null;
        }

        String spanish = product.name().get("es");
        return spanish != null ? spanish : product.name().values().stream().findFirst().orElse(null);
    }

    private String mainImageUrl(TiendanubeProductResponse product) {
        if (product.images() == null || product.images().isEmpty()) {
            return null;
        }

        return product.images().stream()
                .filter(image -> image != null && image.src() != null && !image.src().isBlank())
                .min((left, right) -> Integer.compare(
                        left.position() == null ? Integer.MAX_VALUE : left.position(),
                        right.position() == null ? Integer.MAX_VALUE : right.position()
                ))
                .map(image -> image.src().trim())
                .orElse(null);
    }
}
