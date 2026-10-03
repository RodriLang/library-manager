package com.rodrilang.librarymanager.inventory.pricing.service;

import com.rodrilang.librarymanager.inventory.pricing.model.InventoryPriceImport;
import com.rodrilang.librarymanager.inventory.pricing.model.InventoryPriceImportClassification;
import com.rodrilang.librarymanager.inventory.pricing.model.InventoryPriceImportItem;
import com.rodrilang.librarymanager.inventory.pricing.model.InventoryPriceImportStatus;
import com.rodrilang.librarymanager.inventory.pricing.repository.InventoryPriceImportItemRepository;
import com.rodrilang.librarymanager.inventory.pricing.repository.InventoryPriceImportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InventoryPriceImportApplyWorker {

    private final InventoryPriceImportRepository importRepository;
    private final InventoryPriceImportItemRepository itemRepository;
    private final InventoryPriceService priceService;

    @Transactional
    public void process(Long importId, Long userId) {
        InventoryPriceImport priceImport = importRepository.findById(importId)
                .orElseThrow(() -> new IllegalStateException("No se encontró la importación " + importId + "."));

        if (priceImport.getStatus() != InventoryPriceImportStatus.PROCESSING) {
            return;
        }

        List<InventoryPriceImportItem> items =
                itemRepository.findAllByPriceImportIdOrderByRowNumberAsc(importId);

        int applied = 0;
        int skipped = 0;

        for (InventoryPriceImportItem item : items) {
            if (item.isDiscarded()) {
                continue;
            }

            if (item.getInventory() == null) {
                skipped++;
                continue;
            }

            if (!item.isSelectedForApply()) {
                skipped++;
                continue;
            }

            if (item.getIncomingPrice() == null || item.getIncomingPrice().signum() <= 0) {
                skipped++;
                continue;
            }

            if (isBlocked(item.getClassification())) {
                skipped++;
                continue;
            }

            if (item.getClassification() == InventoryPriceImportClassification.UNCHANGED) {
                priceService.confirmPrice(
                        item.getInventory(),
                        item.getIncomingPrice(),
                        priceImport.getEffectiveFrom(),
                        priceImport.getSourceName()
                );
            } else {
                priceService.upsertImported(
                        item.getInventory(),
                        item.getIncomingPrice(),
                        priceImport.getEffectiveFrom(),
                        priceImport,
                        userId
                );
            }

            item.setApplied(true);
            applied++;
        }

        Instant finishedAt = Instant.now();
        priceImport.setAppliedRows(applied);
        priceImport.setSkippedRows(skipped);
        priceImport.setAppliedAt(finishedAt);
        priceImport.setProcessingFinishedAt(finishedAt);
        priceImport.setProcessingError(null);
        priceImport.setStatus(InventoryPriceImportStatus.APPLIED);

        itemRepository.saveAll(items);
    }

    private boolean isBlocked(InventoryPriceImportClassification classification) {
        return classification == InventoryPriceImportClassification.DUPLICATE_CONFLICT
                || classification == InventoryPriceImportClassification.AMBIGUOUS_MATCH
                || classification == InventoryPriceImportClassification.INVALID_PRICE;
    }
}
