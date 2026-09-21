package com.rodrilang.librarymanager.inventory.bulk.service;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.enums.BookCondition;
import com.rodrilang.librarymanager.integrations.tiendanube.enums.TiendanubeInventoryStatus;
import com.rodrilang.librarymanager.inventory.bulk.dto.request.InventoryBulkSelectionRequest;
import com.rodrilang.librarymanager.inventory.bulk.dto.response.InventoryBulkPreviewResponse;
import com.rodrilang.librarymanager.model.Inventory;
import com.rodrilang.librarymanager.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InventoryBulkQueryService {

    private static final int BATCH_SIZE = 500;

    private final InventoryRepository inventoryRepository;
    private final InventoryBulkSelectionService selectionService;
    private final BookstoreContext bookstoreContext;

    @Transactional(readOnly = true)
    public InventoryBulkPreviewResponse preview(
            InventoryBulkSelectionRequest selection
    ) {
        List<Long> ids =
                selectionService.resolveIds(selection);

        if (ids.isEmpty()) {
            return new InventoryBulkPreviewResponse(
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0
            );
        }

        Long bookstoreId =
                bookstoreContext.getCurrentBookstoreId();

        long active = 0;
        long inactive = 0;

        long withStock = 0;
        long withoutStock = 0;

        long newItems = 0;
        long usedItems = 0;

        long linked = 0;

        for (
                int from = 0;
                from < ids.size();
                from += BATCH_SIZE
        ) {
            int to = Math.min(
                    from + BATCH_SIZE,
                    ids.size()
            );

            List<Long> batchIds =
                    ids.subList(from, to);

            List<Inventory> inventories =
                    inventoryRepository
                            .findAllByBookstoreIdAndIdIn(
                                    bookstoreId,
                                    batchIds
                            );

            for (Inventory inventory : inventories) {
                if (Boolean.TRUE.equals(inventory.getActive())) {
                    active++;
                } else {
                    inactive++;
                }

                if (inventory.getStock() > 0) {
                    withStock++;
                } else {
                    withoutStock++;
                }

                if (inventory.getCondition() == BookCondition.NEW) {
                    newItems++;
                } else {
                    usedItems++;
                }

                if (
                        inventory.getTiendanubeStatus()
                                == TiendanubeInventoryStatus.LINKED
                ) {
                    linked++;
                }
            }
        }

        return new InventoryBulkPreviewResponse(
                ids.size(),
                active,
                inactive,
                withStock,
                withoutStock,
                newItems,
                usedItems,
                linked
        );
    }
}