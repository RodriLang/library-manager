package com.rodrilang.librarymanager.inventory.count.service;

import com.rodrilang.librarymanager.enums.InventoryMovementReferenceType;
import com.rodrilang.librarymanager.enums.InventoryMovementSource;
import com.rodrilang.librarymanager.enums.InventoryMovementType;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.inventory.count.event.InventoryCountStockChangedEvent;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountMode;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountResult;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountSession;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountStatus;
import com.rodrilang.librarymanager.inventory.count.repository.InventoryCountResultRepository;
import com.rodrilang.librarymanager.inventory.count.repository.InventoryCountSessionRepository;
import com.rodrilang.librarymanager.inventory.movement.dto.InventoryStockChangeCommand;
import com.rodrilang.librarymanager.inventory.movement.dto.InventoryStockChangeResult;
import com.rodrilang.librarymanager.inventory.movement.service.InventoryStockService;
import com.rodrilang.librarymanager.model.Inventory;
import com.rodrilang.librarymanager.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class InventoryCountRevertService {

    private final InventoryCountResultRepository resultRepository;
    private final InventoryCountSessionRepository sessionRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryStockService stockService;
    private final ApplicationEventPublisher eventPublisher;

    public void revert(InventoryCountSession session) {
        if (session.getStatus() != InventoryCountStatus.APPLIED
                && session.getStatus() != InventoryCountStatus.APPLIED_WITH_PENDING) {
            throw new BusinessException("Solo se puede revertir un conteo aplicado");
        }

        validateNotSuperseded(session);

        List<InventoryCountResult> appliedResults = resultRepository.findAllBySessionIdOrderById(session.getId()).stream()
                .filter(result -> result.getAppliedAt() != null)
                .toList();

        validateReversal(appliedResults);

        Set<Long> affectedInventoryIds = new HashSet<>();
        for (InventoryCountResult result : appliedResults) {
            Inventory inventory = result.getInventory();
            if (inventory == null) {
                result.setRevertedAt(Instant.now());
                continue;
            }

            boolean activeBeforeRevert = Boolean.TRUE.equals(inventory.getActive());
            if (result.getAppliedDelta() != 0) {
                InventoryStockChangeResult stockResult = stockService.changeStock(
                        inventory.getId(),
                        new InventoryStockChangeCommand(
                                -result.getAppliedDelta(),
                                InventoryMovementType.ADJUSTMENT,
                                InventoryMovementSource.MANUAL,
                                InventoryMovementReferenceType.INVENTORY_COUNT,
                                session.getId().toString(),
                                "Reversión del conteo de inventario"
                        )
                );
                inventory = stockResult.inventory();
                affectedInventoryIds.add(inventory.getId());
            }

            if (inventory.getStock().equals(result.getPreviousQuantity())) {
                inventory.setActive(result.isPreviousActive());
            }

            if (activeBeforeRevert != Boolean.TRUE.equals(inventory.getActive())) {
                affectedInventoryIds.add(inventory.getId());
            }

            result.setRevertedAt(Instant.now());
        }

        session.setStatus(InventoryCountStatus.REVERTED);
        session.setRevertedAt(Instant.now());

        if (!affectedInventoryIds.isEmpty()) {
            eventPublisher.publishEvent(new InventoryCountStockChangedEvent(Set.copyOf(affectedInventoryIds)));
        }
    }

    private void validateNotSuperseded(InventoryCountSession session) {
        if (session.getAppliedAt() == null) {
            return;
        }

        boolean newerAbsoluteCount = sessionRepository.existsByBookstoreIdAndConditionAndModeAndStatusInAndAppliedAtAfter(
                session.getBookstore().getId(),
                session.getCondition(),
                InventoryCountMode.ABSOLUTE,
                Set.of(InventoryCountStatus.APPLIED, InventoryCountStatus.APPLIED_WITH_PENDING),
                session.getAppliedAt()
        );

        if (newerAbsoluteCount) {
            throw new BusinessException(
                    "No se puede revertir este conteo porque existe un conteo absoluto posterior aplicado. "
                            + "Revierta primero el conteo más reciente."
            );
        }
    }

    private void validateReversal(List<InventoryCountResult> results) {
        for (InventoryCountResult result : results) {
            if (result.getInventory() == null || result.getAppliedDelta() <= 0) {
                continue;
            }

            Inventory inventory = inventoryRepository.findById(result.getInventory().getId()).orElse(null);
            if (inventory != null && inventory.getStock() - result.getAppliedDelta() < 0) {
                throw new BusinessException(
                        "No se puede revertir el conteo porque movimientos posteriores consumieron stock del libro: "
                                + result.getBook().getTitle()
                );
            }
        }
    }
}
