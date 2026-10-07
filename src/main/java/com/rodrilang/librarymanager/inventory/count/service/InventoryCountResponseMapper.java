package com.rodrilang.librarymanager.inventory.count.service;

import com.rodrilang.librarymanager.inventory.count.dto.response.InventoryCountBookResponse;
import com.rodrilang.librarymanager.inventory.count.dto.response.InventoryCountItemResponse;
import com.rodrilang.librarymanager.inventory.count.dto.response.InventoryCountResultResponse;
import com.rodrilang.librarymanager.inventory.count.dto.response.InventoryCountSessionResponse;
import com.rodrilang.librarymanager.inventory.count.dto.response.InventoryCountSummaryResponse;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountItem;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountResult;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountSession;
import com.rodrilang.librarymanager.inventory.count.repository.InventoryCountItemRepository;
import com.rodrilang.librarymanager.inventory.count.repository.InventoryCountItemSummaryProjection;
import com.rodrilang.librarymanager.integrations.tiendanube.enums.TiendanubeInventoryStatus;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.model.Inventory;

import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InventoryCountResponseMapper {

    private final InventoryCountItemRepository itemRepository;
    private final InventoryCountPriceResolver priceResolver;

    public InventoryCountSessionResponse toSessionResponse(InventoryCountSession session) {
        InventoryCountItemSummaryProjection summary = itemRepository.summarize(session.getId());

        return new InventoryCountSessionResponse(
                session.getId(),
                session.getMode(),
                session.getPurpose(),
                session.getStatus(),
                session.getCondition(),
                session.getNotes(),
                new InventoryCountSummaryResponse(
                        value(summary.getTotalItems()),
                        value(summary.getTotalUnits()),
                        value(summary.getResolvedItems()),
                        value(summary.getPendingCatalogItems()),
                        value(summary.getPendingPriceItems()),
                        value(summary.getInvalidItems()),
                        value(summary.getSupersededItems()),
                        value(summary.getAppliedItems())
                ),
                session.getDefaultPublishOnTiendanube(),
                session.getDefaultTiendanubePriceSyncEnabled(),
                session.getDefaultMinimumStock(),
                session.getDefaultConsignment(),
                session.getDefaultConsignmentProvider() != null ? session.getDefaultConsignmentProvider().getId() : null,
                session.getDefaultConsignmentProvider() != null ? session.getDefaultConsignmentProvider().getName() : null,
                session.getBaselineAt(),
                session.getReviewedAt(),
                session.getAppliedAt(),
                session.getRevertedAt(),
                session.getCreatedAt(),
                session.getUpdatedAt()
        );
    }

    public InventoryCountItemResponse toItemResponse(InventoryCountItem item) {
        Inventory existing = item.getBook() != null ? priceResolver.existingInventory(item).orElse(null) : null;
        return toItemResponse(item, existing);
    }

    public InventoryCountItemResponse toItemResponse(
            InventoryCountItem item,
            Inventory existing
    ) {
        BigDecimal currentInventoryPrice = existing != null
                ? priceResolver.resolve(item).orElse(null)
                : null;
        BigDecimal effectiveSalePrice = item.getSalePriceOverride() != null
                ? item.getSalePriceOverride()
                : currentInventoryPrice;
        String priceSource = item.getSalePriceOverride() != null
                ? "MANUAL"
                : currentInventoryPrice != null ? "INVENTORY" : "MISSING";

        boolean alreadyPublished = existing != null
                && existing.getTiendanubeStatus() != TiendanubeInventoryStatus.NOT_PUBLISHED;
        boolean publish = alreadyPublished || (item.getPublishOnTiendanubeOverride() != null
                ? item.getPublishOnTiendanubeOverride()
                : existing == null && Boolean.TRUE.equals(item.getSession().getDefaultPublishOnTiendanube()));
        boolean tiendanubePriceSync = item.getTiendanubePriceSyncOverride() != null
                ? item.getTiendanubePriceSyncOverride()
                : existing != null
                    ? Boolean.TRUE.equals(existing.getTiendanubePriceSyncEnabled())
                    : Boolean.TRUE.equals(item.getSession().getDefaultTiendanubePriceSyncEnabled());
        int minimumStock = item.getMinimumStockOverride() != null
                ? item.getMinimumStockOverride()
                : existing != null ? existing.getMinimumStock() : item.getSession().getDefaultMinimumStock();

        return new InventoryCountItemResponse(
                item.getId(),
                item.getRawIdentifier(),
                item.getNormalizedIdentifier(),
                item.getIsbn10(),
                item.getIsbn13(),
                item.getQuantity(),
                item.getStatus(),
                item.getSalePriceOverride(),
                effectiveSalePrice,
                priceSource,
                existing != null,
                publish,
                tiendanubePriceSync,
                minimumStock,
                item.getConsignmentQuantityOverride() != null ? item.getConsignmentQuantityOverride() : (Boolean.TRUE.equals(item.getSession().getDefaultConsignment()) ? item.getQuantity() : 0),
                item.getConsignmentProvider() != null ? item.getConsignmentProvider().getId() : (item.getSession().getDefaultConsignmentProvider() != null ? item.getSession().getDefaultConsignmentProvider().getId() : null),
                item.getConsignmentProvider() != null ? item.getConsignmentProvider().getName() : (item.getSession().getDefaultConsignmentProvider() != null ? item.getSession().getDefaultConsignmentProvider().getName() : null),
                toBookResponse(item.getBook()),
                item.getCatalogCandidate() != null ? item.getCatalogCandidate().getId() : null,
                item.getFirstScannedAt(),
                item.getLastScannedAt(),
                item.getAppliedAt()
        );
    }

    public InventoryCountResultResponse toResultResponse(InventoryCountResult result) {
        return new InventoryCountResultResponse(
                result.getId(),
                toBookResponse(result.getBook()),
                result.getInventory() != null ? result.getInventory().getId() : null,
                result.isBaseline(),
                result.isInventoryExistedBefore(),
                result.isPreviousActive(),
                result.getPreviousQuantity(),
                result.getCountedQuantity(),
                result.getAppliedDelta(),
                result.getResultingQuantity(),
                result.getResultingActive(),
                result.getDifferenceType(),
                result.getAppliedAt(),
                result.getRevertedAt()
        );
    }

    private InventoryCountBookResponse toBookResponse(Book book) {
        return book == null ? null : new InventoryCountBookResponse(book.getId(), book.getPreferredIsbn(), book.getTitle());
    }

    private long value(Long value) {
        return value != null ? value : 0L;
    }
}
