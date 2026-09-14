package com.rodrilang.librarymanager.inventory.count.service;

import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountItem;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountItemStatus;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountMode;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountResult;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountSession;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountStatus;
import com.rodrilang.librarymanager.inventory.count.repository.InventoryCountItemRepository;
import com.rodrilang.librarymanager.inventory.count.repository.InventoryCountResultRepository;
import com.rodrilang.librarymanager.model.Inventory;
import com.rodrilang.librarymanager.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventoryCountReviewService {

    private final InventoryCountItemRepository itemRepository;
    private final InventoryCountResultRepository resultRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryCountPriceResolver priceResolver;
    private final InventoryCountDifferenceCalculator differenceCalculator;
    private final InventoryCountReviewResetService resetService;

    public void review(InventoryCountSession session) {
        if (session.getStatus() != InventoryCountStatus.OPEN && session.getStatus() != InventoryCountStatus.REVIEW) {
            throw new BusinessException("El conteo no puede pasar a revisión en su estado actual");
        }

        resetService.resetReviewData(session);

        Map<Long, InventoryCountResult> results = new HashMap<>();
        resultRepository.findAllBySessionIdOrderById(session.getId()).forEach(result -> results.put(result.getBook().getId(), result));

        if (session.getMode() == InventoryCountMode.ABSOLUTE) {
            results.values().stream()
                    .filter(InventoryCountResult::isBaseline)
                    .filter(InventoryCountResult::isPreviousActive)
                    .forEach(result -> result.setCountedQuantity(0));
        }

        List<InventoryCountItem> knownItems = itemRepository.findAllBySessionIdAndStatusIn(
                session.getId(),
                List.of(InventoryCountItemStatus.RESOLVED, InventoryCountItemStatus.PENDING_PRICE)
        );

        Map<Long, Inventory> inventoriesByBook = knownItems.isEmpty()
                ? Map.of()
                : inventoryRepository.findAllByBookstoreIdAndBookIdInAndCondition(
                                session.getBookstore().getId(),
                                knownItems.stream().map(item -> item.getBook().getId()).toList(),
                                session.getCondition()
                        ).stream()
                        .collect(Collectors.toMap(inventory -> inventory.getBook().getId(), Function.identity()));

        for (InventoryCountItem item : knownItems) {
            refreshPriceStatus(item);
            InventoryCountResult result = results.computeIfAbsent(
                    item.getBook().getId(),
                    ignored -> newResult(session, item, inventoriesByBook.get(item.getBook().getId()))
            );
            result.setCountedQuantity(item.getQuantity());
        }

        results.values().forEach(result -> result.setDifferenceType(differenceCalculator.calculate(session.getMode(), result)));
        resultRepository.saveAll(results.values());

        session.setStatus(InventoryCountStatus.REVIEW);
        session.setReviewedAt(Instant.now());
    }

    private void refreshPriceStatus(InventoryCountItem item) {
        if (item.getStatus() != InventoryCountItemStatus.PENDING_PRICE) {
            return;
        }

        boolean hasPrice = item.getSalePriceOverride() != null || !priceResolver.requiresPrice(
                item.getBook(),
                item.getSession().getBookstore().getId(),
                item.getSession().getCondition()
        );

        if (hasPrice) {
            item.setStatus(InventoryCountItemStatus.RESOLVED);
        }
    }

    private InventoryCountResult newResult(
            InventoryCountSession session,
            InventoryCountItem item,
            Inventory inventory
    ) {
        return InventoryCountResult.builder()
                .session(session)
                .book(item.getBook())
                .inventory(inventory)
                .baseline(false)
                .inventoryExistedBefore(inventory != null)
                .previousActive(inventory != null && Boolean.TRUE.equals(inventory.getActive()))
                .previousQuantity(inventory != null ? inventory.getStock() : 0)
                .build();
    }
}
