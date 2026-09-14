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
import com.rodrilang.librarymanager.model.Book;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InventoryCountResponseMapper {

    private final InventoryCountItemRepository itemRepository;

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
                session.getBaselineAt(),
                session.getReviewedAt(),
                session.getAppliedAt(),
                session.getRevertedAt(),
                session.getCreatedAt(),
                session.getUpdatedAt()
        );
    }

    public InventoryCountItemResponse toItemResponse(InventoryCountItem item) {
        return new InventoryCountItemResponse(
                item.getId(),
                item.getRawIdentifier(),
                item.getNormalizedIdentifier(),
                item.getIsbn10(),
                item.getIsbn13(),
                item.getQuantity(),
                item.getStatus(),
                item.getSalePriceOverride(),
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
