package com.rodrilang.librarymanager.inventory.count.service;

import com.rodrilang.librarymanager.enums.InventoryMovementReferenceType;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.inventory.count.event.InventoryCountStockChangedEvent;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountItem;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountItemStatus;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountMode;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountResult;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountSession;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountStatus;
import com.rodrilang.librarymanager.inventory.count.repository.InventoryCountItemRepository;
import com.rodrilang.librarymanager.inventory.count.repository.InventoryCountResultRepository;
import com.rodrilang.librarymanager.inventory.movement.repository.InventoryMovementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class InventoryCountApplyService {

    private final InventoryCountItemRepository itemRepository;
    private final InventoryCountResultRepository resultRepository;
    private final InventoryMovementRepository movementRepository;
    private final InventoryCountStockOperationService stockOperationService;
    private final InventoryCountSupersessionService supersessionService;
    private final ApplicationEventPublisher eventPublisher;

    public void apply(InventoryCountSession session, boolean allowConcurrentMovements) {
        if (session.getStatus() != InventoryCountStatus.REVIEW) {
            throw new BusinessException("El conteo debe estar en revisión antes de aplicarse");
        }

        validateConcurrentMovements(session, allowConcurrentMovements);

        Map<Long, InventoryCountItem> itemsByBook = new HashMap<>();
        itemRepository.findAllBySessionIdOrderById(session.getId()).stream()
                .filter(item -> item.getBook() != null)
                .forEach(item -> itemsByBook.put(item.getBook().getId(), item));

        Set<Long> affectedInventoryIds = new HashSet<>();
        for (InventoryCountResult result : resultRepository.findAllBySessionIdAndCountedQuantityIsNotNullOrderById(session.getId())) {
            InventoryCountItem item = itemsByBook.get(result.getBook().getId());
            stockOperationService.applyInitial(session, result, item).ifPresent(affectedInventoryIds::add);
        }

        session.setAppliedAt(Instant.now());
        boolean hasPending = itemRepository.existsBySessionIdAndAppliedAtIsNullAndStatusNot(
                session.getId(), InventoryCountItemStatus.SUPERSEDED
        );
        session.setStatus(hasPending ? InventoryCountStatus.APPLIED_WITH_PENDING : InventoryCountStatus.APPLIED);

        supersessionService.supersedeOlderPendingAbsoluteLoads(session);

        if (!affectedInventoryIds.isEmpty()) {
            eventPublisher.publishEvent(new InventoryCountStockChangedEvent(Set.copyOf(affectedInventoryIds)));
        }
    }

    public long countConcurrentMovements(InventoryCountSession session) {
        if (session.getMode() != InventoryCountMode.ABSOLUTE) {
            return 0;
        }

        return movementRepository.countConcurrentAfter(
                session.getBookstore().getId(),
                session.getCondition(),
                session.getBaselineAt(),
                InventoryMovementReferenceType.INVENTORY_COUNT,
                session.getId().toString()
        );
    }

    private void validateConcurrentMovements(InventoryCountSession session, boolean allowed) {
        long movements = countConcurrentMovements(session);
        if (movements > 0 && !allowed) {
            throw new BusinessException(
                    "Se registraron " + movements + " movimientos de stock desde que comenzó el conteo. "
                            + "Revise el informe y confirme explícitamente si desea aplicar igualmente."
            );
        }
    }
}
