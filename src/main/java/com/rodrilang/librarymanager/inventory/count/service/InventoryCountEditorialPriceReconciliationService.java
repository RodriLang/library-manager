package com.rodrilang.librarymanager.inventory.count.service;

import com.rodrilang.librarymanager.inventory.count.model.InventoryCountItem;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountItemStatus;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountSession;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountStatus;
import com.rodrilang.librarymanager.inventory.count.repository.InventoryCountItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InventoryCountEditorialPriceReconciliationService {

    private static final List<InventoryCountStatus> ACTIVE_STATUSES = List.of(
            InventoryCountStatus.OPEN,
            InventoryCountStatus.REVIEW,
            InventoryCountStatus.APPLIED_WITH_PENDING
    );

    private final InventoryCountItemRepository itemRepository;
    private final InventoryCountPriceResolver priceResolver;
    private final InventoryCountReviewService reviewService;
    private final InventoryCountPendingApplyService pendingApplyService;

    @Transactional
    public void reconcile(Collection<Long> bookIds) {
        if (bookIds == null || bookIds.isEmpty()) {
            return;
        }

        List<InventoryCountItem> items = itemRepository.findUnappliedByBookIdsAndSessionStatuses(
                bookIds,
                ACTIVE_STATUSES
        );
        Map<Long, InventoryCountSession> reviewSessions = new LinkedHashMap<>();

        for (InventoryCountItem item : items) {
            boolean hasPrice = item.getSalePriceOverride() != null || !priceResolver.requiresPrice(
                    item.getBook(),
                    item.getSession().getBookstore().getId(),
                    item.getSession().getCondition()
            );
            item.setStatus(hasPrice ? InventoryCountItemStatus.RESOLVED : InventoryCountItemStatus.PENDING_PRICE);

            InventoryCountSession session = item.getSession();
            if (session.getStatus() == InventoryCountStatus.REVIEW) {
                reviewSessions.put(session.getId(), session);
            } else if (session.getStatus() == InventoryCountStatus.APPLIED_WITH_PENDING && hasPrice) {
                pendingApplyService.applyIfReady(item);
            }
        }

        reviewSessions.values().forEach(reviewService::review);
    }
}
