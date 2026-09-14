package com.rodrilang.librarymanager.inventory.count.service;

import com.rodrilang.librarymanager.inventory.count.model.InventoryCountMode;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountResult;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountSession;
import com.rodrilang.librarymanager.inventory.count.repository.InventoryCountResultRepository;
import com.rodrilang.librarymanager.model.Inventory;
import com.rodrilang.librarymanager.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InventoryCountSnapshotService {

    private final InventoryRepository inventoryRepository;
    private final InventoryCountResultRepository resultRepository;

    public void createBaseline(InventoryCountSession session) {
        if (session.getMode() != InventoryCountMode.ABSOLUTE) {
            return;
        }

        List<Inventory> inventory = inventoryRepository.findAllByBookstoreIdAndCondition(
                session.getBookstore().getId(),
                session.getCondition()
        );

        List<InventoryCountResult> results = inventory.stream()
                .map(item -> InventoryCountResult.builder()
                        .session(session)
                        .book(item.getBook())
                        .inventory(item)
                        .baseline(true)
                        .inventoryExistedBefore(true)
                        .previousActive(Boolean.TRUE.equals(item.getActive()))
                        .previousQuantity(item.getStock())
                        .build())
                .toList();

        resultRepository.saveAll(results);
    }
}
