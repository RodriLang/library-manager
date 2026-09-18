package com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.service;

import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeProductResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeVariantResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeProductLink;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TiendanubeImportAnalysisMatchingService {

    private final TiendanubeImportAnalysisContextService contextService;
    private final TiendanubeImportAnalysisIdentifierService identifierService;
    private final TiendanubeImportAnalysisTextMatcher textMatcher;
    private final TiendanubeImportAnalysisResultFactory resultFactory;
    private final TiendanubeImportAnalysisDuplicateTargetResolver duplicateTargetResolver;

    public List<TiendanubeImportAnalysisItemData> analyze(
            Long bookstoreId,
            Long storeId,
            List<TiendanubeProductResponse> products
    ) {
        TiendanubeImportAnalysisMatchContext context = contextService.build(bookstoreId, storeId, products);
        List<TiendanubeImportAnalysisItemData> result = new ArrayList<>();

        for (TiendanubeProductResponse product : products) {
            if (product.variants() == null || product.variants().isEmpty()) {
                continue;
            }

            for (TiendanubeVariantResponse variant : product.variants()) {
                result.add(analyzeVariant(product, variant, context));
            }
        }

        return duplicateTargetResolver.resolve(result);
    }

    private TiendanubeImportAnalysisItemData analyzeVariant(
            TiendanubeProductResponse product,
            TiendanubeVariantResponse variant,
            TiendanubeImportAnalysisMatchContext context
    ) {
        TiendanubeImportAnalysisParsedIdentifier identifier = identifierService.resolve(variant);
        TiendanubeProductLink existingLink = context.linksByVariantId().get(variant.id());

        if (existingLink != null) {
            Inventory inventory = existingLink.getInventory();
            return resultFactory.create(
                    product,
                    variant,
                    identifier,
                    TiendanubeImportAnalysisItemStatus.ALREADY_LINKED,
                    TiendanubeImportAnalysisMatchType.EXISTING_LINK,
                    inventory.getId(),
                    inventory.getBook().getId(),
                    "La variante ya está vinculada con este inventario.",
                    List.of(resultFactory.inventoryCandidate(
                            inventory,
                            1,
                            1,
                            TiendanubeImportAnalysisMatchType.EXISTING_LINK,
                            false
                    ))
            );
        }

        return identifier == null
                ? analyzeWithoutIdentifier(product, variant, context)
                : analyzeWithIdentifier(product, variant, identifier, context);
    }

    private TiendanubeImportAnalysisItemData analyzeWithIdentifier(
            TiendanubeProductResponse product,
            TiendanubeVariantResponse variant,
            TiendanubeImportAnalysisParsedIdentifier identifier,
            TiendanubeImportAnalysisMatchContext context
    ) {
        List<Inventory> exactInventories = contextService.findInventoriesByIdentifier(context, identifier.parsed());

        if (!exactInventories.isEmpty()) {
            return exactInventoryMatch(product, variant, identifier, exactInventories, context);
        }

        List<Book> exactBooks = identifierService.findCatalogBooks(identifier.parsed());

        if (!exactBooks.isEmpty()) {
            return resultFactory.create(
                    product,
                    variant,
                    identifier,
                    TiendanubeImportAnalysisItemStatus.NOT_IN_INVENTORY,
                    identifier.matchType(),
                    null,
                    exactBooks.getFirst().getId(),
                    "El ISBN existe en el catálogo, pero no está cargado como libro nuevo en el inventario de esta librería.",
                    resultFactory.catalogCandidates(exactBooks, TiendanubeImportAnalysisMatchType.CATALOG_MATCH, 1)
            );
        }

        String remoteName = resultFactory.productName(product);
        List<TiendanubeImportAnalysisScoredInventory> inventoryCandidates = textMatcher.scoreInventories(
                remoteName,
                context.inventories()
        );

        if (!inventoryCandidates.isEmpty()) {
            return identifierMismatchInventory(product, variant, identifier, inventoryCandidates, context);
        }

        List<TiendanubeImportAnalysisScoredBook> catalogCandidates = textMatcher.scoreCatalog(remoteName);

        if (!catalogCandidates.isEmpty()) {
            return resultFactory.create(
                    product,
                    variant,
                    identifier,
                    TiendanubeImportAnalysisItemStatus.NOT_IN_INVENTORY,
                    TiendanubeImportAnalysisMatchType.CATALOG_MATCH,
                    null,
                    catalogCandidates.getFirst().book().getId(),
                    "El ISBN remoto no existe en el catálogo, pero el título tiene candidatos que no están en el inventario.",
                    resultFactory.catalogCandidates(catalogCandidates)
            );
        }

        return resultFactory.create(
                product,
                variant,
                identifier,
                TiendanubeImportAnalysisItemStatus.NOT_IN_CATALOG,
                identifier.matchType(),
                null,
                null,
                "El ISBN informado por Tiendanube no existe en el catálogo de Anaquel.",
                List.of()
        );
    }

    private TiendanubeImportAnalysisItemData analyzeWithoutIdentifier(
            TiendanubeProductResponse product,
            TiendanubeVariantResponse variant,
            TiendanubeImportAnalysisMatchContext context
    ) {
        String remoteName = resultFactory.productName(product);
        List<TiendanubeImportAnalysisScoredInventory> inventoryCandidates = textMatcher.scoreInventories(
                remoteName,
                context.inventories()
        );

        if (!inventoryCandidates.isEmpty()) {
            return textualInventoryMatch(product, variant, inventoryCandidates, context);
        }

        List<TiendanubeImportAnalysisScoredBook> catalogCandidates = textMatcher.scoreCatalog(remoteName);

        if (!catalogCandidates.isEmpty()) {
            return resultFactory.create(
                    product,
                    variant,
                    null,
                    TiendanubeImportAnalysisItemStatus.NOT_IN_INVENTORY,
                    TiendanubeImportAnalysisMatchType.CATALOG_MATCH,
                    null,
                    catalogCandidates.getFirst().book().getId(),
                    "Hay libros similares en el catálogo, pero ninguno está en el inventario nuevo de esta librería.",
                    resultFactory.catalogCandidates(catalogCandidates)
            );
        }

        return resultFactory.create(
                product,
                variant,
                null,
                TiendanubeImportAnalysisItemStatus.NOT_IN_CATALOG,
                TiendanubeImportAnalysisMatchType.NONE,
                null,
                null,
                "No se encontraron candidatos suficientes en el inventario ni en el catálogo.",
                List.of()
        );
    }

    private TiendanubeImportAnalysisItemData exactInventoryMatch(
            TiendanubeProductResponse product,
            TiendanubeVariantResponse variant,
            TiendanubeImportAnalysisParsedIdentifier identifier,
            List<Inventory> inventories,
            TiendanubeImportAnalysisMatchContext context
    ) {
        List<TiendanubeImportAnalysisCandidateData> candidates = resultFactory.exactInventoryCandidates(
                inventories,
                identifier.matchType(),
                context
        );

        if (inventories.size() > 1) {
            return resultFactory.create(
                    product,
                    variant,
                    identifier,
                    TiendanubeImportAnalysisItemStatus.CONFLICT,
                    TiendanubeImportAnalysisMatchType.MULTIPLE_MATCHES,
                    null,
                    null,
                    "El ISBN coincide con más de un inventario. Requiere revisión manual.",
                    candidates
            );
        }

        Inventory inventory = inventories.getFirst();

        if (context.linksByInventoryId().containsKey(inventory.getId())) {
            return resultFactory.create(
                    product,
                    variant,
                    identifier,
                    TiendanubeImportAnalysisItemStatus.CONFLICT,
                    identifier.matchType(),
                    inventory.getId(),
                    inventory.getBook().getId(),
                    "El libro coincide por ISBN, pero ese inventario ya está vinculado a otra variante de Tiendanube.",
                    candidates
            );
        }

        return resultFactory.create(
                product,
                variant,
                identifier,
                TiendanubeImportAnalysisItemStatus.READY_TO_LINK,
                identifier.matchType(),
                inventory.getId(),
                inventory.getBook().getId(),
                identifier.parsed().recovered()
                        ? "Coincidencia por ISBN recuperado. Se normalizó el identificador remoto antes de comparar."
                        : "Coincidencia segura por ISBN.",
                candidates
        );
    }

    private TiendanubeImportAnalysisItemData textualInventoryMatch(
            TiendanubeProductResponse product,
            TiendanubeVariantResponse variant,
            List<TiendanubeImportAnalysisScoredInventory> scored,
            TiendanubeImportAnalysisMatchContext context
    ) {
        TiendanubeImportAnalysisScoredInventory best = scored.getFirst();
        TiendanubeImportAnalysisScoredInventory second = scored.size() > 1 ? scored.get(1) : null;
        boolean highConfidence = textMatcher.isHighConfidence(best, second);
        boolean available = !context.linksByInventoryId().containsKey(best.inventory().getId());
        List<TiendanubeImportAnalysisCandidateData> candidates = resultFactory.inventoryCandidates(scored, context);

        if (highConfidence && !available) {
            return resultFactory.create(
                    product,
                    variant,
                    null,
                    TiendanubeImportAnalysisItemStatus.CONFLICT,
                    best.matchType(),
                    best.inventory().getId(),
                    best.inventory().getBook().getId(),
                    "La mejor coincidencia ya está vinculada a otra variante de Tiendanube.",
                    candidates
            );
        }

        return resultFactory.create(
                product,
                variant,
                null,
                highConfidence
                        ? TiendanubeImportAnalysisItemStatus.READY_TO_LINK
                        : TiendanubeImportAnalysisItemStatus.REQUIRES_REVIEW,
                highConfidence
                        ? best.matchType()
                        : scored.size() > 1
                        ? TiendanubeImportAnalysisMatchType.MULTIPLE_MATCHES
                        : best.matchType(),
                best.inventory().getId(),
                best.inventory().getBook().getId(),
                highConfidence
                        ? "Coincidencia textual de alta confianza dentro del inventario físico."
                        : "Hay candidatos en el inventario, pero la coincidencia necesita confirmación.",
                candidates
        );
    }

    private TiendanubeImportAnalysisItemData identifierMismatchInventory(
            TiendanubeProductResponse product,
            TiendanubeVariantResponse variant,
            TiendanubeImportAnalysisParsedIdentifier identifier,
            List<TiendanubeImportAnalysisScoredInventory> scored,
            TiendanubeImportAnalysisMatchContext context
    ) {
        TiendanubeImportAnalysisScoredInventory best = scored.getFirst();
        boolean available = !context.linksByInventoryId().containsKey(best.inventory().getId());

        return resultFactory.create(
                product,
                variant,
                identifier,
                available
                        ? TiendanubeImportAnalysisItemStatus.REQUIRES_REVIEW
                        : TiendanubeImportAnalysisItemStatus.CONFLICT,
                scored.size() > 1
                        ? TiendanubeImportAnalysisMatchType.MULTIPLE_MATCHES
                        : best.matchType(),
                best.inventory().getId(),
                best.inventory().getBook().getId(),
                available
                        ? "El ISBN remoto no existe en el catálogo, pero el título coincide con inventario. Revisá antes de vincular."
                        : "El título coincide con un inventario ya vinculado y el ISBN remoto no existe en el catálogo.",
                resultFactory.inventoryCandidates(scored, context)
        );
    }
}
