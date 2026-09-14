package com.rodrilang.librarymanager.inventory.count.service;

import com.rodrilang.librarymanager.inventory.count.event.InventoryCountStockChangedEvent;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountItem;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountItemStatus;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountMode;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountResult;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountSession;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountStatus;
import com.rodrilang.librarymanager.inventory.count.repository.InventoryCountItemRepository;
import com.rodrilang.librarymanager.inventory.count.repository.InventoryCountResultRepository;
import com.rodrilang.librarymanager.inventory.count.repository.InventoryCountSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class InventoryCountPendingApplyService {

    private final InventoryCountItemRepository itemRepository;
    private final InventoryCountResultRepository resultRepository;
    private final InventoryCountSessionRepository sessionRepository;
    private final InventoryCountDifferenceCalculator differenceCalculator;
    private final InventoryCountStockOperationService stockOperationService;
    private final ApplicationEventPublisher eventPublisher;

    public void applyIfReady(InventoryCountItem item) {
        InventoryCountSession session = item.getSession();
        if (session.getStatus() != InventoryCountStatus.APPLIED_WITH_PENDING
                || item.getAppliedAt() != null
                || item.getStatus() != InventoryCountItemStatus.RESOLVED) {
            return;
        }

        if (isSuperseded(session)) {
            item.setStatus(InventoryCountItemStatus.SUPERSEDED);
            refreshSessionStatus(session);
            return;
        }

        InventoryCountResult result = resultRepository.findBySessionIdAndBookId(session.getId(), item.getBook().getId())
                .orElseGet(() -> createLateResult(session, item));

        Long affectedInventoryId = null;
        if (result.getAppliedAt() == null) {
            result.setCountedQuantity(item.getQuantity());
            result.setDifferenceType(differenceCalculator.calculate(session.getMode(), result));
            affectedInventoryId = stockOperationService.applyInitial(session, result, item).orElse(null);
        } else if (session.getMode() == InventoryCountMode.ABSOLUTE) {
            int previousCounted = result.getCountedQuantity() != null ? result.getCountedQuantity() : 0;
            result.setCountedQuantity(item.getQuantity());
            result.setDifferenceType(differenceCalculator.calculate(session.getMode(), result));
            affectedInventoryId = stockOperationService
                    .applyAbsoluteCorrection(session, result, item, previousCounted)
                    .orElse(null);
        }

        if (affectedInventoryId != null) {
            eventPublisher.publishEvent(new InventoryCountStockChangedEvent(Set.of(affectedInventoryId)));
        }

        refreshSessionStatus(session);
    }

    public void refreshSessionStatus(InventoryCountSession session) {
        if (session.getStatus() == InventoryCountStatus.APPLIED_WITH_PENDING
                && !itemRepository.existsBySessionIdAndAppliedAtIsNullAndStatusNot(
                        session.getId(),
                        InventoryCountItemStatus.SUPERSEDED
                )) {
            session.setStatus(InventoryCountStatus.APPLIED);
        }
    }

    private boolean isSuperseded(InventoryCountSession session) {
        if (session.getAppliedAt() == null) {
            return false;
        }

        return sessionRepository.existsByBookstoreIdAndConditionAndModeAndStatusInAndAppliedAtAfter(
                session.getBookstore().getId(),
                session.getCondition(),
                InventoryCountMode.ABSOLUTE,
                Set.of(InventoryCountStatus.APPLIED, InventoryCountStatus.APPLIED_WITH_PENDING),
                session.getAppliedAt()
        );
    }

    private InventoryCountResult createLateResult(InventoryCountSession session, InventoryCountItem item) {
        boolean lateAbsolute = session.getMode() == InventoryCountMode.ABSOLUTE
                && session.getStatus() == InventoryCountStatus.APPLIED_WITH_PENDING;

        InventoryCountResult result = InventoryCountResult.builder()
                .session(session)
                .book(item.getBook())
                .baseline(false)
                .inventoryExistedBefore(false)
                .previousActive(false)
                .previousQuantity(0)
                .countedQuantity(lateAbsolute ? 0 : item.getQuantity())
                .appliedAt(lateAbsolute ? Instant.now() : null)
                .build();

        result.setDifferenceType(differenceCalculator.calculate(session.getMode(), result));
        return resultRepository.save(result);
    }
}
