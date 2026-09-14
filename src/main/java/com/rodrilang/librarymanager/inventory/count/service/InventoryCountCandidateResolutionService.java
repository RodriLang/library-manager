package com.rodrilang.librarymanager.inventory.count.service;

import com.rodrilang.librarymanager.inventory.count.model.InventoryCountItem;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountItemStatus;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountSession;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountStatus;
import com.rodrilang.librarymanager.inventory.count.repository.InventoryCountItemRepository;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class InventoryCountCandidateResolutionService {

    private final InventoryCountItemRepository itemRepository;
    private final BookRepository bookRepository;
    private final InventoryCountPriceResolver priceResolver;
    private final InventoryCountReviewService reviewService;
    private final InventoryCountPendingApplyService pendingApplyService;

    @Transactional
    public void resolve(Long candidateId, Long bookId) {
        Book book = bookRepository.findById(bookId).orElse(null);
        if (book == null) {
            return;
        }

        List<InventoryCountItem> items = itemRepository.findAllByCatalogCandidateId(candidateId);
        Set<Long> reviewedSessions = new HashSet<>();

        for (InventoryCountItem item : items) {
            item.setBook(book);
            item.setStatus(resolveStatus(item));

            InventoryCountSession session = item.getSession();
            if (session.getStatus() == InventoryCountStatus.REVIEW && reviewedSessions.add(session.getId())) {
                reviewService.review(session);
            } else if (session.getStatus() == InventoryCountStatus.APPLIED_WITH_PENDING) {
                pendingApplyService.applyIfReady(item);
            }
        }
    }

    private InventoryCountItemStatus resolveStatus(InventoryCountItem item) {
        if (item.getSalePriceOverride() != null) {
            return InventoryCountItemStatus.RESOLVED;
        }

        return priceResolver.requiresPrice(
                item.getBook(),
                item.getSession().getBookstore().getId(),
                item.getSession().getCondition()
        ) ? InventoryCountItemStatus.PENDING_PRICE : InventoryCountItemStatus.RESOLVED;
    }
}
