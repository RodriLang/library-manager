package com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.service;

import com.rodrilang.librarymanager.enums.BookCondition;
import com.rodrilang.librarymanager.service.BookService;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.dto.TiendanubeImportAnalysisCreateBookRequest;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeProductLink;
import com.rodrilang.librarymanager.integrations.tiendanube.enums.TiendanubeInventoryStatus;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.dto.TiendanubeImportAnalysisCreateInventoryRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.enums.TiendanubeImportAnalysisItemStatus;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.model.TiendanubeImportAnalysisItemLock;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.repository.TiendanubeImportAnalysisItemWriteRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.importanalysis.repository.TiendanubeImportAnalysisRunRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.job.enums.TiendanubeJobType;
import com.rodrilang.librarymanager.integrations.tiendanube.job.service.TiendanubeJobRequestService;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeProductLinkRepository;
import com.rodrilang.librarymanager.model.Inventory;
import com.rodrilang.librarymanager.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class TiendanubeImportAnalysisResolutionItemService {

    private final TiendanubeImportAnalysisItemWriteRepository itemWriteRepository;
    private final TiendanubeImportAnalysisRunRepository runRepository;
    private final InventoryRepository inventoryRepository;
    private final TiendanubeProductLinkRepository productLinkRepository;
    private final TiendanubeJobRequestService jobRequestService;
    private final TiendanubeImportAnalysisInventoryService analysisInventoryService;
    private final BookService bookService;
    private final com.rodrilang.librarymanager.integrations.tiendanube.client.TiendanubeClient tiendanubeClient;
    private final com.rodrilang.librarymanager.integrations.tiendanube.management.service.BookstoreCommercialCategoryService commercialCategories;

    @Transactional
    public Long resolve(
            Long runId,
            Long itemId,
            Long bookstoreId,
            Long inventoryId,
            boolean syncStock,
            boolean refreshCounts
    ) {
        TiendanubeImportAnalysisItemLock item = itemWriteRepository
                .lockItem(runId, itemId, bookstoreId)
                .orElseThrow(() -> new BusinessException(
                        "No se encontró un caso resoluble para el análisis indicado"
                ));

        validateItemStatus(item.status());

        Inventory inventory = inventoryRepository.findByIdForUpdate(inventoryId)
                .orElseThrow(() -> new BusinessException("No existe el inventario con id " + inventoryId));

        validateInventory(inventory, bookstoreId);
        validateCanLink(item, inventory);

        completeMissingMeasures(item, inventory.getBook());
        linkAndResolve(runId, item, inventory, syncStock, refreshCounts);
        return inventory.getId();
    }

    @Transactional
    public Long createInventoryAndResolve(
            Long runId,
            Long itemId,
            Long bookstoreId,
            TiendanubeImportAnalysisCreateInventoryRequest request
    ) {
        TiendanubeImportAnalysisItemLock item = itemWriteRepository
                .lockItem(runId, itemId, bookstoreId)
                .orElseThrow(() -> new BusinessException(
                        "No se encontró un caso resoluble para el análisis indicado"
                ));

        validateItemStatus(item.status());

        if (!itemWriteRepository.existsCatalogCandidate(item.id(), request.bookId())) {
            throw new BusinessException(
                    "El libro seleccionado no pertenece a las coincidencias de catálogo de este caso"
            );
        }

        Inventory inventory = analysisInventoryService.createOrReactivate(bookstoreId, request);
        validateInventory(inventory, bookstoreId);
        validateCanLink(item, inventory);

        completeMissingMeasures(item, inventory.getBook());
        linkAndResolve(
                runId,
                item,
                inventory,
                request.shouldSyncStock(),
                true
        );

        return inventory.getId();
    }

    /** Atomic local creation of book, inventory and link, with a locked analysis item. */
    @Transactional
    public Long createBookInventoryAndResolve(
            Long runId, Long itemId, Long bookstoreId,
            TiendanubeImportAnalysisCreateBookRequest request
    ) {
        TiendanubeImportAnalysisItemLock item = itemWriteRepository
                .lockItem(runId, itemId, bookstoreId)
                .orElseThrow(() -> new BusinessException("No se encontró el caso de Tiendanube"));
        validateItemStatus(item.status());

        Long bookId = bookService.create(request.book()).id();
        var inventoryRequest = new TiendanubeImportAnalysisCreateInventoryRequest(
                bookId, request.salePrice(), request.initialStock(),
                request.minimumStock(), request.syncStock(), request.resolvedPriceSource());
        Inventory inventory = analysisInventoryService.createOrReactivate(bookstoreId, inventoryRequest);
        validateInventory(inventory, bookstoreId);
        validateCanLink(item, inventory);
        linkAndResolve(runId, item, inventory, request.shouldSyncStock(), true);
        return inventory.getId();
    }

    /** Preview shipping measurements for the exact variant being linked. */
    @Transactional
    public RemoteMeasures remoteMeasures(Long runId, Long itemId, Long bookstoreId) {
        var item = itemWriteRepository.lockItem(runId, itemId, bookstoreId)
                .orElseThrow(() -> new BusinessException("No se encontró el caso de Tiendanube"));
        var variant = remoteVariant(item);
        if (variant == null) return new RemoteMeasures(null, null, null, null);
        return new RemoteMeasures(grams(variant.weight()), positive(variant.width()),
                positive(variant.height()), positive(variant.depth()));
    }

    public record RemoteMeasures(java.math.BigDecimal weightGrams, java.math.BigDecimal widthCm,
                                 java.math.BigDecimal heightCm, java.math.BigDecimal depthCm) {}

    /** Tiendanube reports shipping measurements (kg and cm); only fill missing book values. */
    private void completeMissingMeasures(TiendanubeImportAnalysisItemLock item, com.rodrilang.librarymanager.model.Book book) {
        var variant = remoteVariant(item);
        if (variant == null) return;
        if (book.getWeightGrams() == null) book.setWeightGrams(grams(variant.weight()));
        if (book.getWidthCm() == null) book.setWidthCm(positive(variant.width()));
        if (book.getHeightCm() == null) book.setHeightCm(positive(variant.height()));
        if (book.getDepthCm() == null) book.setDepthCm(positive(variant.depth()));
    }

    private com.rodrilang.librarymanager.dto.request.BookRequest withRemoteMeasures(
            TiendanubeImportAnalysisItemLock item, com.rodrilang.librarymanager.dto.request.BookRequest b) {
        var v = remoteVariant(item);
        if (v == null) return b;
        return new com.rodrilang.librarymanager.dto.request.BookRequest(
                b.isbn(), b.withoutIsbn(), b.title(), b.subtitle(), b.description(), b.language(),
                b.publicationYear(), b.publicationMonth(), b.coverUrl(), b.categoryName(), b.genreName(),
                b.pageCount(), b.weightGrams() == null ? grams(v.weight()) : b.weightGrams(),
                b.widthCm() == null ? positive(v.width()) : b.widthCm(),
                b.heightCm() == null ? positive(v.height()) : b.heightCm(),
                b.depthCm() == null ? positive(v.depth()) : b.depthCm(), b.publisherId(), b.authorIds());
    }

    private com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeVariantResponse remoteVariant(
            TiendanubeImportAnalysisItemLock item) {
        // Remote lookup is intentionally best-effort: network problems must not block linking.
        try {
            var product = tiendanubeClient.getProduct(item.storeId(), item.productId());
            if (product.variants() == null) return null;
            return product.variants().stream()
                    .filter(v -> java.util.Objects.equals(v.id(), item.variantId())).findFirst().orElse(null);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private java.math.BigDecimal positive(java.math.BigDecimal value) {
        return value != null && value.signum() > 0 ? value : null;
    }
    private java.math.BigDecimal grams(java.math.BigDecimal kilograms) {
        var valid = positive(kilograms);
        return valid == null ? null : valid.multiply(java.math.BigDecimal.valueOf(1000));
    }

    @Transactional
    public void refreshCounts(Long runId) {
        runRepository.refreshCounts(runId);
    }

    @Transactional
    public void ignore(Long runId, Long itemId, Long bookstoreId) {
        if (!itemWriteRepository.markIgnored(runId, itemId, bookstoreId, Instant.now())) {
            throw new BusinessException("El caso no puede ignorarse o ya fue resuelto");
        }

        runRepository.refreshCounts(runId);
    }

    private void linkAndResolve(
            Long runId,
            TiendanubeImportAnalysisItemLock item,
            Inventory inventory,
            boolean syncStock,
            boolean refreshCounts
    ) {
        TiendanubeProductLink link = TiendanubeProductLink.builder()
                .inventory(inventory)
                .tiendanubeStoreId(item.storeId())
                .tiendanubeProductId(item.productId())
                .tiendanubeVariantId(item.variantId())
                .sku(item.remoteSku())
                .active(true)
                .lastSyncedAt(null)
                .lastError(null)
                .build();

        productLinkRepository.save(link);
        inventory.setTiendanubeStatus(TiendanubeInventoryStatus.LINKED);
        // Recupera las categorías comerciales de la publicación si el inventario
        // todavía no fue categorizado en Anaquel. No escribe en Tiendanube.
        try {
            var product = tiendanubeClient.getProduct(item.storeId(), item.productId());
            if (product != null && product.categories() != null) {
                var remoteIds = product.categories().stream().map(node -> {
                    if (node == null) return (Long) null;
                    if (node.isNumber()) return node.longValue();
                    if (node.isObject() && node.hasNonNull("id")) return node.path("id").asLong();
                    return (Long) null;
                }).filter(java.util.Objects::nonNull).distinct().toList();
                commercialCategories.assignRemoteCategoriesIfEmpty(
                        inventory.getBookstore().getId(), inventory.getId(), remoteIds);
            }
        } catch (com.rodrilang.librarymanager.integrations.tiendanube.exception.TiendanubeApiException e) {
            // Una falla de red no debe bloquear la vinculación del inventario.
            org.slf4j.LoggerFactory.getLogger(getClass()).warn(
                    "No se pudieron importar las categorías de publicación {}", item.productId(), e);
        }


        Instant now = Instant.now();

        if (!itemWriteRepository.markResolved(item.id(), inventory.getId(), now)) {
            throw new BusinessException("El caso ya fue resuelto por otro proceso");
        }

        itemWriteRepository.markInventoryUnavailableForOtherItems(
                runId,
                item.id(),
                inventory.getId(),
                now
        );

        if (refreshCounts) {
            runRepository.refreshCounts(runId);
        }

        if (syncStock) {
            jobRequestService.enqueueAutomaticLinked(inventory.getId(), TiendanubeJobType.SYNC_STOCK);
        }
    }

    private void validateItemStatus(TiendanubeImportAnalysisItemStatus status) {
        if (status == TiendanubeImportAnalysisItemStatus.RESOLVED
                || status == TiendanubeImportAnalysisItemStatus.IGNORED
                || status == TiendanubeImportAnalysisItemStatus.ALREADY_LINKED) {
            throw new BusinessException("El caso ya está cerrado y no puede volver a vincularse");
        }
    }

    private void validateInventory(Inventory inventory, Long bookstoreId) {
        if (!bookstoreId.equals(inventory.getBookstore().getId())) {
            throw new BusinessException("El inventario seleccionado pertenece a otra librería");
        }

        if (!Boolean.TRUE.equals(inventory.getActive())) {
            throw new BusinessException("El inventario seleccionado está inactivo");
        }

        if (inventory.getCondition() != BookCondition.NEW) {
            throw new BusinessException("Tiendanube solo puede vincularse con inventario en condición NEW");
        }

        if (!Boolean.TRUE.equals(inventory.getBook().getActive())) {
            throw new BusinessException("El libro seleccionado está inactivo en el catálogo");
        }
    }

    private void validateCanLink(
            TiendanubeImportAnalysisItemLock item,
            Inventory inventory
    ) {
        if (productLinkRepository
                .findByInventoryIdAndTiendanubeStoreIdAndActiveTrue(inventory.getId(), item.storeId())
                .isPresent()) {
            throw new BusinessException("El inventario ya está vinculado con otra publicación de Tiendanube");
        }

        if (productLinkRepository
                .findByTiendanubeStoreIdAndTiendanubeVariantIdAndActiveTrue(item.storeId(), item.variantId())
                .isPresent()) {
            throw new BusinessException("La variante de Tiendanube ya está vinculada a otro inventario");
        }
    }
}
