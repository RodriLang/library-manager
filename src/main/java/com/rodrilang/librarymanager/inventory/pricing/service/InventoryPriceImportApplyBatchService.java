package com.rodrilang.librarymanager.inventory.pricing.service;

import com.rodrilang.librarymanager.inventory.pricing.model.InventoryPriceImport;
import com.rodrilang.librarymanager.inventory.pricing.model.InventoryPriceImportClassification;
import com.rodrilang.librarymanager.inventory.pricing.model.InventoryPriceImportItem;
import com.rodrilang.librarymanager.inventory.pricing.repository.InventoryPriceImportItemRepository;
import com.rodrilang.librarymanager.inventory.pricing.repository.InventoryPriceImportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InventoryPriceImportApplyBatchService {
    private final InventoryPriceImportRepository importRepository;
    private final InventoryPriceImportItemRepository itemRepository;
    private final InventoryPriceService priceService;

    @Transactional
    public BatchResult process(long importId, Long userId, List<InventoryPriceImportItem> items) {
        InventoryPriceImport priceImport = importRepository.findById(importId).orElseThrow();
        List<InventoryPriceService.ImportedPriceCommand> commands = new ArrayList<>();
        List<InventoryPriceImportItem> applicableItems = new ArrayList<>();
        int skipped = 0;

        for (InventoryPriceImportItem item : items) {
            if (item.isDiscarded() || item.getInventory() == null || item.getIncomingPrice() == null
                    || item.getIncomingPrice().signum() <= 0 || isBlocked(item.getClassification())) {
                skipped++;
                continue;
            }
            commands.add(new InventoryPriceService.ImportedPriceCommand(item.getInventory(), item.getIncomingPrice()));
            applicableItems.add(item);
        }

        priceService.upsertImportedBatch(commands, priceImport, userId);
        for (InventoryPriceImportItem item : applicableItems) item.setApplied(true);
        itemRepository.saveAll(applicableItems);
        return new BatchResult(applicableItems.size(), skipped);
    }

    private boolean isBlocked(InventoryPriceImportClassification classification) {
        return classification == InventoryPriceImportClassification.DUPLICATE_CONFLICT
                || classification == InventoryPriceImportClassification.AMBIGUOUS_MATCH
                || classification == InventoryPriceImportClassification.INVALID_PRICE;
    }

    public record BatchResult(int applied, int skipped) {}
}
