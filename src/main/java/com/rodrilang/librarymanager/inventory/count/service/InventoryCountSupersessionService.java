package com.rodrilang.librarymanager.inventory.count.service;

import com.rodrilang.librarymanager.inventory.count.model.InventoryCountItem;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountItemStatus;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountMode;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountSession;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountStatus;
import com.rodrilang.librarymanager.inventory.count.repository.InventoryCountItemRepository;
import com.rodrilang.librarymanager.inventory.count.repository.InventoryCountSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InventoryCountSupersessionService {

    private final InventoryCountSessionRepository sessionRepository;
    private final InventoryCountItemRepository itemRepository;

    public void supersedeOlderPendingAbsoluteLoads(InventoryCountSession appliedSession) {
        if (appliedSession.getMode() != InventoryCountMode.ABSOLUTE || appliedSession.getAppliedAt() == null) {
            return;
        }

        List<InventoryCountSession> olderSessions =
                sessionRepository.findAllByBookstoreIdAndConditionAndModeAndStatusAndAppliedAtBefore(
                        appliedSession.getBookstore().getId(),
                        appliedSession.getCondition(),
                        InventoryCountMode.ABSOLUTE,
                        InventoryCountStatus.APPLIED_WITH_PENDING,
                        appliedSession.getAppliedAt()
                );

        for (InventoryCountSession older : olderSessions) {
            List<InventoryCountItem> staleItems = itemRepository.findAllBySessionIdOrderById(older.getId()).stream()
                    .filter(item -> item.getAppliedAt() == null)
                    .filter(item -> item.getStatus() != InventoryCountItemStatus.SUPERSEDED)
                    .toList();

            staleItems.forEach(item -> item.setStatus(InventoryCountItemStatus.SUPERSEDED));
            if (!staleItems.isEmpty()) {
                itemRepository.saveAll(staleItems);
            }
            older.setStatus(InventoryCountStatus.APPLIED);
        }
    }
}
